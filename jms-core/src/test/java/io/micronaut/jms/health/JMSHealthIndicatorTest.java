package io.micronaut.jms.health;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ConnectionMetaData;
import jakarta.jms.JMSException;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JMSHealthIndicatorTest {

    @Test
    void reportsUpWithTheProviderOfEachConnectionFactory() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "JMSHealthIndicatorTest"))) {
            HealthResult result = result(context);

            assertEquals(HealthStatus.UP, result.getStatus());
            assertEquals(Map.of("up", Map.of(
                "status", "UP",
                "provider", "Stub MQ",
                "providerVersion", "1.2.3",
                "jmsVersion", "3.1")), result.getDetails());
        }
    }

    @Test
    void reportsDownWhenAConnectionCannotBeOpened() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "JMSHealthIndicatorTest",
            "spec.broken", "true"))) {
            HealthResult result = result(context);

            assertEquals(HealthStatus.DOWN, result.getStatus());
            Map<?, ?> details = (Map<?, ?>) result.getDetails();
            assertEquals("UP", ((Map<?, ?>) details.get("up")).get("status"));
            assertEquals(Map.of(
                "status", "DOWN",
                "error", "jakarta.jms.JMSException: broker unavailable"), details.get("broken"));
        }
    }

    @Test
    void canBeDisabled() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "JMSHealthIndicatorTest",
            JMSHealthIndicator.ENABLED, "false"))) {
            assertFalse(context.containsBean(JMSHealthIndicator.class));
        }
    }

    private static HealthResult result(ApplicationContext context) {
        List<HealthResult> results = new ArrayList<>();
        context.getBean(JMSHealthIndicator.class).getResult().subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(HealthResult healthResult) {
                results.add(healthResult);
            }

            @Override
            public void onError(Throwable throwable) {
                throw new AssertionError(throwable);
            }

            @Override
            public void onComplete() {
            }
        });
        assertEquals(1, results.size());
        return results.get(0);
    }

    @Factory
    @Requires(property = "spec.name", value = "JMSHealthIndicatorTest")
    static class StubPools {

        @Singleton
        @Named("up")
        JMSConnectionPool up() {
            ConnectionMetaData metaData = stub(ConnectionMetaData.class, name -> switch (name) {
                case "getJMSProviderName" -> "Stub MQ";
                case "getProviderVersion" -> "1.2.3";
                case "getJMSVersion" -> "3.1";
                default -> null;
            });
            Connection connection = stub(Connection.class, name -> "getMetaData".equals(name) ? metaData : null);
            return new JMSConnectionPool(stub(ConnectionFactory.class, name -> connection), 0, 1);
        }

        @Singleton
        @Named("broken")
        @Requires(property = "spec.broken", value = "true")
        JMSConnectionPool broken() {
            return new JMSConnectionPool(stub(ConnectionFactory.class, name -> {
                throw new JMSException("broker unavailable");
            }), 0, 1);
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
