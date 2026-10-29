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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JMSHealthIndicatorTest {

    static final List<Thread> CONNECTING_THREADS = new CopyOnWriteArrayList<>();

    @Test
    void connectsOnlyWhenSubscribedAndNotOnTheCallingThread() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "JMSHealthIndicatorTest"))) {
            CONNECTING_THREADS.clear();
            var publisher = context.getBean(JMSHealthIndicator.class).getResult();

            assertTrue(CONNECTING_THREADS.isEmpty(), "no connection is opened before the result is subscribed");

            await(publisher);

            assertEquals(1, CONNECTING_THREADS.size());
            assertNotSame(Thread.currentThread(), CONNECTING_THREADS.get(0));
        }
    }

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
        try {
            return await(context.getBean(JMSHealthIndicator.class).getResult());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static HealthResult await(org.reactivestreams.Publisher<HealthResult> publisher) throws Exception {
        CompletableFuture<HealthResult> result = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(HealthResult healthResult) {
                result.complete(healthResult);
            }

            @Override
            public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                result.completeExceptionally(new AssertionError("completed without a result"));
            }
        });
        return result.get(10, TimeUnit.SECONDS);
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
            return new JMSConnectionPool(stub(ConnectionFactory.class, name -> {
                CONNECTING_THREADS.add(Thread.currentThread());
                return connection;
            }), 0, 1);
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
