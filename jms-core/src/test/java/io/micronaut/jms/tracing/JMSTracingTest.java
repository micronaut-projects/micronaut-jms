package io.micronaut.jms.tracing;

import io.micronaut.context.ApplicationContext;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.observation.JMSObservation;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.micronaut.jms.observation.ObservationStubs.message;
import static io.micronaut.jms.observation.ObservationStubs.stub;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JMSTracingTest {

    private static final ContextKey<String> REQUEST_ID = ContextKey.named("request-id");

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private ApplicationContext context;
    private JMSTracing tracing;

    @BeforeEach
    void start() {
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
            .setPropagators(ContextPropagators.create(TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(), new RequestIdPropagator())))
            .build();
        context = ApplicationContext.builder().singletons(openTelemetry).start();
        tracing = context.getBean(JMSTracing.class);
    }

    @AfterEach
    void stop() {
        context.close();
    }

    @Test
    void theConsumerSpanContinuesTheTraceThatTheProducerSpanPropagatesInMessageProperties() throws JMSException {
        TextMessage message = message("order", null, "ID:1");

        try (var ignored = Context.root().with(REQUEST_ID, "r-1").makeCurrent()) {
            tracing.send(JMSDestinationType.QUEUE, "orders", message).complete(null);
        }
        assertNotNull(message.getStringProperty("traceparent"));
        assertEquals("r-1", message.getStringProperty("x" + JMSTracing.DASH + "request" + JMSTracing.DASH + "id"));

        JMSObservation processing = tracing.process(JMSDestinationType.QUEUE, "orders", message);
        String processSpanId = Span.current().getSpanContext().getSpanId();
        processing.complete(null);
        assertFalse(Span.current().getSpanContext().isValid(), "the span is current only while processing");

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(2, spans.size());
        SpanData send = spans.get(0);
        SpanData process = spans.get(1);
        assertEquals("send orders", send.getName());
        assertEquals(SpanKind.PRODUCER, send.getKind());
        assertEquals("process orders", process.getName());
        assertEquals(SpanKind.CONSUMER, process.getKind());
        assertEquals(send.getTraceId(), process.getTraceId());
        assertEquals(send.getSpanId(), process.getParentSpanId());
        assertEquals(processSpanId, process.getSpanId());
        for (SpanData span : spans) {
            assertEquals("jms", span.getAttributes().get(AttributeKey.stringKey("messaging.system")));
            assertEquals("orders", span.getAttributes().get(AttributeKey.stringKey("messaging.destination.name")));
            assertEquals("ID:1", span.getAttributes().get(AttributeKey.stringKey("messaging.message.id")));
            assertEquals(StatusCode.UNSET, span.getStatus().getStatusCode());
        }
    }

    @Test
    void failuresAreRecordedOnTheSpans() {
        tracing.send(JMSDestinationType.TOPIC, "prices", message("up", null, null)).complete(new IllegalStateException("send failed"));
        tracing.process(JMSDestinationType.TOPIC, "prices", message("up", null, null)).complete(new IllegalArgumentException("bad"));

        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(List.of(IllegalStateException.class.getName(), IllegalArgumentException.class.getName()),
            spans.stream().map(span -> span.getAttributes().get(AttributeKey.stringKey("error.type"))).toList());
        for (SpanData span : spans) {
            assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
            assertEquals(1, span.getEvents().size(), "the exception event");
            assertNull(span.getAttributes().get(AttributeKey.stringKey("messaging.message.id")));
            assertFalse(span.getParentSpanContext().isValid());
        }
    }

    @Test
    void messagesWhosePropertiesCannotBeReadOrWrittenAreStillTraced() {
        Message readOnly = stub(Message.class, (name, args) -> {
            throw new JMSException(name + " is not supported");
        });
        Message broken = stub(Message.class, (name, args) -> {
            throw new IllegalStateException(name + " failed");
        });

        tracing.send(JMSDestinationType.QUEUE, "orders", readOnly).complete(null);
        tracing.process(JMSDestinationType.QUEUE, "orders", readOnly).complete(null);
        tracing.send(JMSDestinationType.QUEUE, "orders", broken).complete(null);
        tracing.process(JMSDestinationType.QUEUE, "orders", broken).complete(null);
        tracing.send(JMSDestinationType.QUEUE, "orders", null).complete(null);
        tracing.process(JMSDestinationType.QUEUE, "orders", null).complete(null);

        assertEquals(6, exporter.getFinishedSpanItems().size());
        assertFalse(Span.current().getSpanContext().isValid());
    }

    @Test
    void canBeDisabled() {
        try (ApplicationContext disabled = ApplicationContext.builder()
            .properties(Map.of(JMSTracing.ENABLED, "false"))
            .singletons(OpenTelemetry.noop())
            .start()) {
            assertFalse(disabled.containsBean(JMSTracing.class));
        }
    }

    /**
     * Propagates a request ID in a header with dashes, and finds it among the keys of the carrier.
     */
    private static final class RequestIdPropagator implements TextMapPropagator {

        private static final String HEADER = "x-request-id";

        @Override
        public Collection<String> fields() {
            return List.of(HEADER);
        }

        @Override
        public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
            String requestId = context.get(REQUEST_ID);
            if (requestId != null) {
                setter.set(carrier, HEADER, requestId);
            }
        }

        @Override
        public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
            for (String key : getter.keys(carrier)) {
                if (HEADER.equals(key)) {
                    String requestId = getter.get(carrier, key);
                    return requestId == null ? context : context.with(REQUEST_ID, requestId);
                }
            }
            return context;
        }
    }
}
