package io.micronaut.jms.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.listener.GlobalJMSListenerErrorHandler;
import io.micronaut.jms.listener.GlobalJMSListenerSuccessHandler;
import jakarta.inject.Singleton;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import jakarta.jms.Topic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JMSListenerMetricsTest {

    @Test
    void countsHandledMessagesByDestinationAndResult() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "JMSListenerMetricsTest"))) {
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            JMSListenerMetrics metrics = context.getBean(JMSListenerMetrics.class);
            assertTrue(context.getBeansOfType(GlobalJMSListenerSuccessHandler.class).contains(metrics));
            assertTrue(context.getBeansOfType(GlobalJMSListenerErrorHandler.class).contains(metrics));

            Queue queue = stub(Queue.class, name -> "getQueueName".equals(name) ? "orders" : null);
            Topic topic = stub(Topic.class, name -> "getTopicName".equals(name) ? "prices" : null);
            Destination other = stub(Destination.class, name -> null);

            metrics.handle(null, message(queue));
            metrics.handle(null, message(queue));
            metrics.handle(null, message(topic));
            metrics.handle(null, message(other));
            metrics.handle(null, message(null));
            metrics.handle(null, stub(Message.class, name -> {
                throw new JMSException("closed");
            }));
            metrics.handle(null, message(queue), new IllegalStateException("boom"));

            assertEquals(2, count(registry, "orders", "success"));
            assertEquals(1, count(registry, "prices", "success"));
            assertEquals(1, count(registry, "Destination stub", "success"));
            assertEquals(2, count(registry, "unknown", "success"));
            assertEquals(1, registry.get(JMSListenerMetrics.MESSAGES)
                .tags("destination", "orders", "result", "failure", "exception", "IllegalStateException")
                .counter().count());
            assertEquals(GlobalJMSListenerSuccessHandler.DEFAULT_POSITION, metrics.getOrder());
        }
    }

    @Test
    void canBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "JMSListenerMetricsTest",
            JMSListenerMetrics.ENABLED, "false"))) {
            assertFalse(context.containsBean(JMSListenerMetrics.class));
        }
    }

    private static double count(MeterRegistry registry, String destination, String result) {
        return registry.get(JMSListenerMetrics.MESSAGES).tags("destination", destination, "result", result).counter().count();
    }

    private static Message message(Destination destination) {
        return stub(Message.class, name -> "getJMSDestination".equals(name) ? destination : null);
    }

    @Factory
    @Requires(property = "spec.name", value = "JMSListenerMetricsTest")
    static class Registry {

        @Singleton
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private static <T> T stub(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> type.getSimpleName() + " stub";
                default -> answer.answer(method.getName());
            }));
    }

    @FunctionalInterface
    private interface Answer {
        Object answer(String method) throws JMSException;
    }
}
