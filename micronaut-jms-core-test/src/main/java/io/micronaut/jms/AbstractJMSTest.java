package io.micronaut.jms;

import io.micronaut.context.ApplicationContext;
import io.micronaut.jms.annotations.Header;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.listener.JMSListenerContainer;
import io.micronaut.jms.listener.JMSListenerContainerFactory;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.model.JMSHeaders;
import io.micronaut.jms.model.MessageHeader;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.templates.JmsConsumer;
import io.micronaut.jms.templates.JmsProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class AbstractJMSTest {

    private static final CountDownLatch QUEUE_LATCH = new CountDownLatch(100);
    private static final CountDownLatch TOPIC_LATCH = new CountDownLatch(100);

    private ConnectionFactory connectionFactory;

    @Inject
    private ApplicationContext context;

    @BeforeEach
    public void setup() {
        if (connectionFactory == null) {
            connectionFactory = getConnectionFactory();
        }
    }

    /***
     * @return a {@link ConnectionFactory} for the test broker.
     */
    protected abstract ConnectionFactory getConnectionFactory();

    /***
     * Tests sending a message to a test queue on a broker.
     */
    @Test
    public void testSendMessage() {
        final JmsProducer producer = new JmsProducer(JMSDestinationType.QUEUE);
        producer.setConnectionFactory(connectionFactory);
        producer.setSerializer(new DefaultSerializerDeserializer());

        producer.send("test-queue", "test-message");

        final JmsConsumer consumer = new JmsConsumer(JMSDestinationType.QUEUE);
        consumer.setConnectionFactory(connectionFactory);
        consumer.setDeserializer(new DefaultSerializerDeserializer());

        final String message = consumer.receive("test-queue", String.class);

        assertNotNull(message, "Message should not be null");
        assertEquals("test-message", message);
    }

    /***
     *
     * Tests setting up a {@link JMSListenerContainer} manually and
     *      sending a large number of messages to it.
     *
     * @throws InterruptedException
     */
    @Test
    public void testListener() throws InterruptedException {
        final JmsProducer producer = new JmsProducer(JMSDestinationType.QUEUE);
        producer.setConnectionFactory(connectionFactory);
        producer.setSerializer(new DefaultSerializerDeserializer());

        for (int i = 2; i < 100; i++) {
            producer.send("test-queue-3", "test-message-" + i);
        }

        final CountDownLatch latch = new CountDownLatch(98);

        final JMSListenerContainer<String> listener = new JMSListenerContainer<>(
                connectionFactory,
                JMSDestinationType.QUEUE);
        listener.setThreadPoolSize(10);
        listener.setMaxThreadPoolSize(20);
        listener.registerListener("test-queue-3", message -> {
            System.err.println("Received message " + message +
                    " on thread " + Thread.currentThread().getName() +
                    " at " + new Date());
            latch.countDown();
        }, String.class);

        latch.await(2L, TimeUnit.SECONDS);

        assertEquals(0, latch.getCount());

        if (!listener.shutdown()) {
            fail("Failed to shutdown JMS listener. There are still open connections.");
        }
    }

    /***
     *
     * Tests setting up a {@link JMSListenerContainer} for a {@link javax.jms.Queue}
     *      using the {@link JMSListener} annotation.
     *
     * @throws InterruptedException
     */
    @Test
    public void testJMSListenerAnnotationDriven() throws InterruptedException {
        final JMSListenerContainerFactory listenerFactory = context.getBean(JMSListenerContainerFactory.class);

        assertNotNull(listenerFactory.getRegisteredListener("test-queue-2"), "Listener is null");

        final JmsProducer producer = new JmsProducer(JMSDestinationType.QUEUE);
        producer.setConnectionFactory(connectionFactory);
        producer.setSerializer(new DefaultSerializerDeserializer());

        for (int i = 0; i < 100; i++) {
            producer.send("test-queue-2", "test-message-" + i,
                    new MessageHeader("JMSCorrelationID", "test-corr-id"),
                    new MessageHeader("X-Arbitrary-Header", "arbitrary-value"));
        }

        QUEUE_LATCH.await(1L, TimeUnit.SECONDS);

        assertEquals(0, QUEUE_LATCH.getCount());
    }

    /***
     *
     * Tests setting up a {@link JMSListenerContainer} for a {@link javax.jms.Topic}
     *      using the {@link JMSListener} annotation.
     *
     * @throws InterruptedException
     */
    @Test
    public void testJMSListenerTopicAnnotationDriven() throws InterruptedException {
        final JMSListenerContainerFactory listenerFactory = context.getBean(JMSListenerContainerFactory.class);

        assertNotNull(listenerFactory.getRegisteredListener("my-topic"), "Listener is null");

        final JmsProducer producer = new JmsProducer(JMSDestinationType.TOPIC);
        producer.setConnectionFactory(connectionFactory);
        producer.setSerializer(new DefaultSerializerDeserializer());

        for (int i = 0; i < 100; i++) {
            producer.send("my-topic", "test-message-" + i,
                    new MessageHeader(JMSHeaders.JMS_PRIORITY, Integer.toString(2)),
                    new MessageHeader("X-Topic-Header", "arbitrary-value"));
        }

        TOPIC_LATCH.await(1L, TimeUnit.SECONDS);

        assertEquals(0, TOPIC_LATCH.getCount());
    }

    /***
     * @return defines an {@link JMSConnectionFactory} in the {@link io.micronaut.context.BeanContext}
     *      to be used by a {@link JMSListener}.
     */
    @JMSConnectionFactory("activeMqConnectionFactory")
    public ConnectionFactory activeMqConnectionFactory() {
        return getConnectionFactory();
    }

    @JMSListener("activeMqConnectionFactory")
    static class TestListener {
        @Queue(
                destination = "test-queue-2",
                concurrency = "1-5",
                transacted = true,
                acknowledgement = Session.CLIENT_ACKNOWLEDGE)
        public void handle(
                String message,
                @Header(JMSHeaders.JMS_CORRELATION_ID) String correlationId,
                @Header("X-Arbitrary-Header") String arbitraryHeader) {
            if ("test-corr-id".equals(correlationId) &&
                    "arbitrary-value".equals(arbitraryHeader)) {
                QUEUE_LATCH.countDown();
            }
            System.err.println("CorrelationID: " + correlationId);
            System.err.println("X-Arbitrary-Header: " + arbitraryHeader);
            System.err.println("Message: " + message);
        }

        @Topic(
                destination = "my-topic")
        public void receive(
                String message,
                @Header(JMSHeaders.JMS_PRIORITY) Integer priority,
                @Header("X-Topic-Header") String header) {
            if (priority.equals(2) &&
                    "arbitrary-value".equals(header)) {
                TOPIC_LATCH.countDown();
            }
            System.err.println("JMSPriority: " + priority);
            System.err.println("X-Topic-Header: " + header);
            System.err.println("Message: " + message);
        }
    }

}
