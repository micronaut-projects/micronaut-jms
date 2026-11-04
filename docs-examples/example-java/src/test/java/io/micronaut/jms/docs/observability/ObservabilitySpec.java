package io.micronaut.jms.docs.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.health.JMSHealthIndicator;
import io.micronaut.jms.metrics.JMSMetrics;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.messaging.annotation.MessageBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilitySpec {

    ApplicationContext context;

    @AfterEach
    void close() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void theHealthIndicatorReportsTheProviderOfEachConnectionFactory() {
        context = start(Map.of());

        HealthResult result = first(context.getBean(JMSHealthIndicator.class).getResult());

        assertEquals(HealthStatus.UP, result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> factory = (Map<String, Object>) ((Map<String, Object>) result.getDetails()).get(CONNECTION_FACTORY_BEAN_NAME);
        assertEquals("UP", factory.get("status"));
        assertTrue(String.valueOf(factory.get("provider")).contains("ActiveMQ"), String.valueOf(factory));
    }

    @Test
    void listenerMessagesAreCountedAndTimedByDestinationAndResult() {
        context = start(Map.of());
        MeterRegistry registry = context.getBean(MeterRegistry.class);
        ObservedProducer producer = context.getBean(ObservedProducer.class);

        producer.send("ok");
        producer.sendFailing("boom");

        await().atMost(20, SECONDS).until(() -> count(registry, JMSMetrics.LISTENER_MESSAGES, "observed", "success") == 1
            && count(registry, JMSMetrics.LISTENER_MESSAGES, "observed_failing", "failure") == 1);
        assertEquals("IllegalStateException", registry.get(JMSMetrics.LISTENER_MESSAGES)
            .tag("destination", "observed_failing").counter().getId().getTag("exception"));
        Timer timer = registry.get(JMSMetrics.LISTENER_DURATION).tag("destination", "observed").tag("result", "success").timer();
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(SECONDS) > 0);
    }

    @Test
    void producerMessagesAreCountedAndTimedByDestination() {
        context = start(Map.of());
        MeterRegistry registry = context.getBean(MeterRegistry.class);
        ObservedProducer producer = context.getBean(ObservedProducer.class);

        producer.send("one");
        producer.send("two");

        assertEquals(2, count(registry, JMSMetrics.PRODUCER_MESSAGES, "observed", "success"));
        assertEquals(2, registry.get(JMSMetrics.PRODUCER_DURATION).tag("destination", "observed").timer().count());
    }

    @Test
    void bothCanBeDisabled() {
        context = start(Map.of(JMSHealthIndicator.ENABLED, false, JMSMetrics.ENABLED, false));

        assertFalse(context.containsBean(JMSHealthIndicator.class));
        assertFalse(context.containsBean(JMSMetrics.class));
    }

    private static <T> T first(Publisher<T> publisher) {
        CompletableFuture<T> result = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(T item) {
                result.complete(item);
            }

            @Override
            public void onError(Throwable throwable) {
                result.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                result.complete(null);
            }
        });
        return result.join();
    }

    private static double count(MeterRegistry registry, String name, String destination, String result) {
        Counter counter = registry.find(name).tag("destination", destination).tag("result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    private static ApplicationContext start(Map<String, Object> extra) {
        String broker = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> config = new HashMap<>(extra);
        config.put("micronaut.jms.activemq.classic.connectionString", "vm://" + broker + "?broker.persistent=false");
        config.put("micronaut.jms.activemq.classic.enabled", true);
        config.put("micronaut.jms.activemq.classic.username", "activemq");
        config.put("micronaut.jms.activemq.classic.password", "activemq");
        config.put("spec.name", "ObservabilitySpec");
        return ApplicationContext.builder().properties(config).environments("test").singletons(new SimpleMeterRegistry()).start();
    }

    @Requires(property = "spec.name", value = "ObservabilitySpec")
    @JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
    interface ObservedProducer {
        @Queue("observed")
        void send(@MessageBody String body);

        @Queue("observed_failing")
        void sendFailing(@MessageBody String body);
    }

    @Requires(property = "spec.name", value = "ObservabilitySpec")
    @JMSListener(CONNECTION_FACTORY_BEAN_NAME)
    static class ObservedListener {
        @Queue("observed")
        void receive(@MessageBody String body) {
        }

        @Queue("observed_failing")
        void fail(@MessageBody String body) {
            throw new IllegalStateException(body);
        }
    }
}
