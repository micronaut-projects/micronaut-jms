package example;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.messaging.annotation.MessageBody;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Durable subscriptions keep the messages published while the application is stopped; a plain topic listener does not.
 */
@Testcontainers(disabledWithoutDocker = true)
class DurableSubscriptionWhileStoppedTest {

    private static final String SPEC_NAME = "DurableSubscriptionWhileStoppedTest";
    private static final String LISTENERS_ENABLED = "while-stopped.listeners.enabled";
    private static final List<String> PUBLISHED_WHILE_STOPPED = List.of("order-1", "order-2", "order-3");

    @Test
    void durableSubscriptionsReceiveMessagesPublishedWhileTheApplicationWasStopped() {
        // the first run creates the subscriptions
        try (ApplicationContext ignored = ApplicationContext.run(properties(true))) {
            // started and stopped
        }

        // an application without the listeners publishes while they are stopped
        try (ApplicationContext publisherOnly = ApplicationContext.run(properties(false))) {
            OrderPublisher publisher = publisherOnly.getBean(OrderPublisher.class);
            PUBLISHED_WHILE_STOPPED.forEach(publisher::publish);
        }

        try (ApplicationContext restarted = ApplicationContext.run(properties(true))) {
            OrderListeners listeners = restarted.getBean(OrderListeners.class);

            await().atMost(30, SECONDS).until(() -> listeners.unsharedDurable.size() >= PUBLISHED_WHILE_STOPPED.size()
                && listeners.sharedDurable.size() >= PUBLISHED_WHILE_STOPPED.size());
            assertEquals(Set.copyOf(PUBLISHED_WHILE_STOPPED), Set.copyOf(listeners.unsharedDurable));
            assertEquals(Set.copyOf(PUBLISHED_WHILE_STOPPED), Set.copyOf(listeners.sharedDurable));
            assertTrue(listeners.plain.isEmpty(), "a plain topic listener does not receive messages published before it started");
        }
    }

    private static Map<String, Object> properties(boolean listenersEnabled) {
        Map<String, Object> properties = new HashMap<>(ActiveMqArtemis.getProperties());
        properties.put("spec.name", SPEC_NAME);
        properties.put(LISTENERS_ENABLED, String.valueOf(listenersEnabled));
        return properties;
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
    interface OrderPublisher {

        @Topic("orders-while-stopped")
        void publish(@MessageBody String order);
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Requires(property = LISTENERS_ENABLED, value = "true")
    @JMSListener(CONNECTION_FACTORY_BEAN_NAME)
    static class OrderListeners {

        final Queue<String> unsharedDurable = new ConcurrentLinkedQueue<>();
        final Queue<String> sharedDurable = new ConcurrentLinkedQueue<>();
        final Queue<String> plain = new ConcurrentLinkedQueue<>();

        @Topic(value = "orders-while-stopped", subscription = "orders-audit", durable = true)
        void audit(@MessageBody String order) {
            unsharedDurable.add(order);
        }

        @Topic(value = "orders-while-stopped", subscription = "orders-billing", durable = true, shared = true)
        void bill(@MessageBody String order) {
            sharedDurable.add(order);
        }

        @Topic("orders-while-stopped")
        void preview(@MessageBody String order) {
            plain.add(order);
        }
    }
}
