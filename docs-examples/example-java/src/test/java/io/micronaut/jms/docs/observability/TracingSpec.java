package io.micronaut.jms.docs.observability;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.tracing.JMSTracing;
import io.micronaut.messaging.annotation.MessageBody;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TracingSpec {

    static final BlockingQueue<SpanContext> CURRENT_SPANS = new LinkedBlockingQueue<>();

    final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    ApplicationContext context;

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
        CURRENT_SPANS.clear();
    }

    @Test
    void theConsumerSpanContinuesTheTraceOfTheProducer() throws InterruptedException {
        context = start(Map.of());
        OpenTelemetry openTelemetry = context.getBean(OpenTelemetry.class);
        TracedProducer producer = context.getBean(TracedProducer.class);

        Span parent = openTelemetry.getTracer("test").spanBuilder("request").startSpan();
        try (Scope ignored = parent.makeCurrent()) {
            producer.send("hello");
        } finally {
            parent.end();
        }

        SpanContext current = CURRENT_SPANS.poll(20, SECONDS);
        assertNotNull(current);
        await().atMost(20, SECONDS).until(() -> spans("process traced").size() == 1);
        SpanData send = spans("send traced").get(0);
        SpanData process = spans("process traced").get(0);
        assertEquals(SpanKind.PRODUCER, send.getKind());
        assertEquals(parent.getSpanContext().getSpanId(), send.getParentSpanId());
        assertEquals(SpanKind.CONSUMER, process.getKind());
        assertEquals(send.getTraceId(), process.getTraceId());
        assertEquals(send.getSpanId(), process.getParentSpanId());
        assertEquals(process.getSpanId(), current.getSpanId(), "the consumer span is current in the listener");
        assertEquals("jms", process.getAttributes().asMap().entrySet().stream()
            .filter(e -> e.getKey().getKey().equals("messaging.system")).findFirst().orElseThrow().getValue());
        assertEquals(send.getAttributes().asMap().entrySet().stream()
                .filter(e -> e.getKey().getKey().equals("messaging.message.id")).findFirst().orElseThrow().getValue(),
            process.getAttributes().asMap().entrySet().stream()
                .filter(e -> e.getKey().getKey().equals("messaging.message.id")).findFirst().orElseThrow().getValue());
    }

    @Test
    void aFailingListenerEndsTheConsumerSpanWithAnError() {
        context = start(Map.of());

        context.getBean(TracedProducer.class).sendFailing("boom");

        await().atMost(20, SECONDS).until(() -> spans("process traced_failing").size() == 1);
        SpanData process = spans("process traced_failing").get(0);
        assertEquals(StatusCode.ERROR, process.getStatus().getStatusCode());
        assertEquals("exception", process.getEvents().get(0).getName());
    }

    @Test
    void tracingCanBeDisabled() {
        context = start(Map.of(JMSTracing.ENABLED, false));

        assertFalse(context.containsBean(JMSTracing.class));
    }

    private List<SpanData> spans(String name) {
        return exporter.getFinishedSpanItems().stream().filter(span -> span.getName().equals(name)).toList();
    }

    private ApplicationContext start(Map<String, Object> extra) {
        String broker = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> config = new HashMap<>(extra);
        config.put("micronaut.jms.activemq.classic.connectionString", "vm://" + broker + "?broker.persistent=false");
        config.put("micronaut.jms.activemq.classic.enabled", true);
        config.put("micronaut.jms.activemq.classic.username", "activemq");
        config.put("micronaut.jms.activemq.classic.password", "activemq");
        config.put("spec.name", "TracingSpec");
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
        return ApplicationContext.builder().properties(config).environments("test").singletons(openTelemetry).start();
    }

    @Requires(property = "spec.name", value = "TracingSpec")
    @JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
    interface TracedProducer {
        @Queue("traced")
        void send(@MessageBody String body);

        @Queue("traced_failing")
        void sendFailing(@MessageBody String body);
    }

    @Requires(property = "spec.name", value = "TracingSpec")
    @JMSListener(CONNECTION_FACTORY_BEAN_NAME)
    static class TracedListener {
        @Queue("traced")
        void receive(@MessageBody String body) {
            CURRENT_SPANS.add(Span.current().getSpanContext());
        }

        @Queue("traced_failing")
        void fail(@MessageBody String body) {
            throw new IllegalStateException(body);
        }
    }
}
