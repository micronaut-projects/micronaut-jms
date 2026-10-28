package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest
class SharedSubscriptionTest implements TestPropertyProvider {

    private static final String SPEC_NAME = "SharedSubscriptionTest";
    private static final int MESSAGE_COUNT = 20;

    @Inject
    InvoicePublisher publisher;

    @Inject
    InvoiceListeners listeners;

    @Test
    void sharedDurableSubscriptionDeliversEachMessageToOneConsumer() {
        List<String> sent = IntStream.range(0, MESSAGE_COUNT).mapToObj(i -> "durable-" + i).toList();
        sent.forEach(publisher::publishDurable);

        await().atMost(30, SECONDS).until(() -> listeners.durableA.size() + listeners.durableB.size() >= MESSAGE_COUNT);
        assertSplitBetweenConsumers(sent, listeners.durableA, listeners.durableB);
    }

    @Test
    void sharedNonDurableSubscriptionDeliversEachMessageToOneConsumer() {
        List<String> sent = IntStream.range(0, MESSAGE_COUNT).mapToObj(i -> "non-durable-" + i).toList();
        sent.forEach(publisher::publishNonDurable);

        await().atMost(30, SECONDS).until(() -> listeners.nonDurableA.size() + listeners.nonDurableB.size() >= MESSAGE_COUNT);
        assertSplitBetweenConsumers(sent, listeners.nonDurableA, listeners.nonDurableB);
    }

    private static void assertSplitBetweenConsumers(List<String> sent, Queue<String> consumerA, Queue<String> consumerB) {
        // both consumers must take part, or a failed registration would go unnoticed
        assertFalse(consumerA.isEmpty(), "the first consumer received no messages");
        assertFalse(consumerB.isEmpty(), "the second consumer received no messages");
        assertEquals(sent.size(), consumerA.size() + consumerB.size(), "every message is delivered to exactly one consumer");
        Set<String> received = new HashSet<>(consumerA);
        received.addAll(consumerB);
        assertEquals(Set.copyOf(sent), received);
    }

    @Override
    public @NonNull Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>(ActiveMqArtemis.getProperties());
        properties.put("spec.name", SPEC_NAME);
        return properties;
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
    interface InvoicePublisher {

        @Topic("invoices-durable")
        void publishDurable(@MessageBody String invoice);

        @Topic("invoices-non-durable")
        void publishNonDurable(@MessageBody String invoice);
    }

    /**
     * Two consumers on each shared subscription: the broker splits the messages between them.
     */
    @Requires(property = "spec.name", value = SPEC_NAME)
    @JMSListener(CONNECTION_FACTORY_BEAN_NAME)
    static class InvoiceListeners {

        final Queue<String> durableA = new ConcurrentLinkedQueue<>();
        final Queue<String> durableB = new ConcurrentLinkedQueue<>();
        final Queue<String> nonDurableA = new ConcurrentLinkedQueue<>();
        final Queue<String> nonDurableB = new ConcurrentLinkedQueue<>();

        @Topic(value = "invoices-durable", subscription = "invoice-billing", durable = true, shared = true)
        void durableA(@MessageBody String invoice) {
            durableA.add(invoice);
        }

        @Topic(value = "invoices-durable", subscription = "invoice-billing", durable = true, shared = true)
        void durableB(@MessageBody String invoice) {
            durableB.add(invoice);
        }

        @Topic(value = "invoices-non-durable", subscription = "invoice-preview", shared = true)
        void nonDurableA(@MessageBody String invoice) {
            nonDurableA.add(invoice);
        }

        @Topic(value = "invoices-non-durable", subscription = "invoice-preview", shared = true)
        void nonDurableB(@MessageBody String invoice) {
            nonDurableB.add(invoice);
        }
    }
}
