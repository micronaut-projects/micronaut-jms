package io.micronaut.jms.tracing.opentelemetry;

import io.micronaut.context.ApplicationContext;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.jms.observation.JMSMessageObserver;
import io.micronaut.jms.observation.JMSObservation;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.micronaut.jms.tracing.opentelemetry.MessageStubs.message;
import static io.micronaut.jms.tracing.opentelemetry.MessageStubs.stub;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JMSTracingTest {

    private static final ContextKey<String> REQUEST_ID = ContextKey.named("request-id");

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private JMSTracing tracing;

    @BeforeEach
    void start() {
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build())
            .setPropagators(ContextPropagators.create(TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(), new RequestIdPropagator())))
            .build();
        tracing = new JMSTracing(openTelemetry);
    }

    @Test
    void theConsumerSpanContinuesTheTraceThatTheProducerSpanPropagatesInMessageProperties() throws JMSException {
        TextMessage message = message("order", "ID:1");

        try (var ignored = Context.root().with(REQUEST_ID, "r-1").makeCurrent()) {
            JMSObservation sending = tracing.send(JMSDestinationType.QUEUE, "orders");
            assertNull(message.getStringProperty("traceparent"), "the context is injected once the message is created");
            sending.beforeSend(message);
            sending.complete(null);
        }
        assertNotNull(message.getStringProperty("traceparent"));
        assertEquals("r-1", message.getStringProperty("x" + JMSTracing.DASH + "request" + JMSTracing.DASH + "id"));

        JMSObservation processing = tracing.process(JMSDestinationType.QUEUE, "orders", message);
        String processSpanId = processing.propagatedContext(PropagatedContext.empty())
            .propagate(() -> Span.current().getSpanContext().getSpanId());
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
        // a send that fails before the message is created, for example to connect
        tracing.send(JMSDestinationType.TOPIC, "prices").complete(new IllegalStateException("send failed"));
        tracing.process(JMSDestinationType.TOPIC, "prices", message("up", null)).complete(new IllegalArgumentException("bad"));

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

        for (Message message : List.of(readOnly, broken)) {
            JMSObservation sending = tracing.send(JMSDestinationType.QUEUE, "orders");
            sending.beforeSend(message);
            sending.complete(null);
            tracing.process(JMSDestinationType.QUEUE, "orders", message).complete(null);
        }
        tracing.process(JMSDestinationType.QUEUE, "orders", null).complete(null);

        assertEquals(5, exporter.getFinishedSpanItems().size());
        assertFalse(Span.current().getSpanContext().isValid());
    }

    @Test
    void theConsumerSpanIsCurrentInThePropagatedContextWhileTheMessageIsProcessed() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            JMSObservation processing = tracing.process(JMSDestinationType.QUEUE, "orders", message("order", "ID:1"));
            PropagatedContext context = processing.propagatedContext(PropagatedContext.getOrEmpty());
            Callable<String> currentSpanId = () -> Span.current().getSpanContext().getSpanId();

            String processSpanId = context.propagateCall(() -> {
                OpenTelemetryPropagationContext element = PropagatedContext.get().find(OpenTelemetryPropagationContext.class).orElseThrow();
                assertEquals(currentSpanId.call(), Span.fromContext(element.context()).getSpanContext().getSpanId());
                assertEquals(currentSpanId.call(), executor.submit(PropagatedContext.get().wrap(currentSpanId)).get(),
                    "the span follows the context to another thread");
                return currentSpanId.call();
            });
            assertFalse(Span.fromContext(executor.submit(Context::current).get()).getSpanContext().isValid());
            processing.complete(null);

            assertFalse(PropagatedContext.getOrEmpty().find(OpenTelemetryPropagationContext.class).isPresent());
            assertFalse(Span.current().getSpanContext().isValid());
            assertEquals(processSpanId, exporter.getFinishedSpanItems().get(0).getSpanId());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void tracesWithTheOpenTelemetryOfMicronautTracingAndCanBeDisabled() {
        InMemorySpanExporter tracingExporter = InMemorySpanExporter.create();
        try (ApplicationContext context = ApplicationContext.builder()
            .properties(Map.of("otel.traces.exporter", "none"))
            .singletons(SimpleSpanProcessor.create(tracingExporter))
            .start()) {
            JMSTracing bean = context.getBean(JMSTracing.class);
            assertTrue(context.getBeansOfType(JMSMessageObserver.class).contains(bean));

            JMSObservation sending = bean.send(JMSDestinationType.QUEUE, "orders");
            TextMessage message = message("order", "ID:1");
            sending.beforeSend(message);
            sending.complete(null);

            assertEquals(List.of("send orders"), tracingExporter.getFinishedSpanItems().stream().map(SpanData::getName).toList());
        }
        try (ApplicationContext disabled = ApplicationContext.builder()
            .properties(Map.of(JMSTracing.ENABLED, "false", "otel.traces.exporter", "none"))
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
