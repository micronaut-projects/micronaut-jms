package io.micronaut.jms.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.order.Ordered;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.observation.JMSMessageObserver;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.micronaut.jms.observation.ObservationStubs.message;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JMSMetricsTest {

    @Test
    void countsAndTimesTheMessagesThatListenersProcessAndProducersSend() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "JMSMetricsTest"))) {
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            JMSMetrics metrics = context.getBean(JMSMetrics.class);
            assertTrue(context.getBeansOfType(JMSMessageObserver.class).contains(metrics));
            assertEquals(Ordered.HIGHEST_PRECEDENCE, metrics.getOrder());

            metrics.process(JMSDestinationType.QUEUE, "orders", message("a", null, null)).complete(null);
            metrics.process(JMSDestinationType.QUEUE, "orders", message("b", null, null)).complete(null);
            metrics.process(JMSDestinationType.QUEUE, "orders", message("c", null, null)).complete(new IllegalStateException());
            metrics.send(JMSDestinationType.TOPIC, "prices").complete(null);

            assertEquals(2, registry.get(JMSMetrics.LISTENER_MESSAGES)
                .tags("destination", "orders", "result", "success").counter().count());
            assertEquals(1, registry.get(JMSMetrics.LISTENER_MESSAGES)
                .tags("destination", "orders", "result", "failure", "exception", "IllegalStateException").counter().count());
            assertEquals(2, registry.get(JMSMetrics.LISTENER_DURATION)
                .tags("destination", "orders", "result", "success").timer().count());
            assertEquals(1, registry.get(JMSMetrics.PRODUCER_MESSAGES)
                .tags("destination", "prices", "result", "success").counter().count());
            assertEquals(1, registry.get(JMSMetrics.PRODUCER_DURATION)
                .tags("destination", "prices", "result", "success").timer().count());
        }
    }

    @Test
    void canBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "JMSMetricsTest",
            JMSMetrics.ENABLED, "false"))) {
            assertFalse(context.containsBean(JMSMetrics.class));
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "JMSMetricsTest")
    static class Registry {

        @Singleton
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
