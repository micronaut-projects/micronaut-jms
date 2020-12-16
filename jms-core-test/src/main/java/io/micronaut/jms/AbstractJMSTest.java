/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jms;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.listener.JMSListenerContainer;
import io.micronaut.jms.listener.JMSListenerContainerFactory;
import io.micronaut.jms.model.MessageHeader;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.templates.JmsConsumer;
import io.micronaut.jms.templates.JmsProducer;
import io.micronaut.messaging.annotation.Body;
import io.micronaut.messaging.annotation.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;
import static io.micronaut.jms.model.JMSDestinationType.TOPIC;
import static io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID;
import static io.micronaut.jms.model.JMSHeaders.JMS_PRIORITY;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.jms.Session.CLIENT_ACKNOWLEDGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractJMSTest {

    protected static final CountDownLatch QUEUE_LATCH = new CountDownLatch(100);
    protected static final CountDownLatch TOPIC_LATCH = new CountDownLatch(100);

    @Inject
    protected ApplicationContext applicationContext;

    protected JMSConnectionPool pool;

    @BeforeEach
    public void setup() {
        pool = applicationContext.getBean(JMSConnectionPool.class,
            Qualifiers.byName("activeMqConnectionFactory"));
    }

    /***
     * Tests sending a message to a test queue on a broker.
     */
    @Test
    public void testSendMessage() {

        final JmsProducer producer = new JmsProducer(QUEUE);
        producer.setConnectionPool(pool);
        producer.setSerializer(DefaultSerializerDeserializer.getInstance());

        producer.send("test-queue", "test-message");

        final JmsConsumer consumer = new JmsConsumer(QUEUE);
        consumer.setConnectionPool(pool);
        consumer.setDeserializer(DefaultSerializerDeserializer.getInstance());

        final String message = consumer.receive("test-queue", String.class);

        assertNotNull(message, "Message must not be null");
        assertEquals("test-message", message);
    }

    /***
     * Tests sending a message to a test queue on a broker.
     */
    @Test
    public void testSendMessageAnnotationDriven() {
        TestProducer producer = applicationContext.getBean(TestProducer.class);
        assertNotNull(producer);

        MessageObject message = new MessageObject();
        message.setId(1);
        message.setDescription("This is a test message.");
        message.setFlag(true);
        message.setTags(Collections.singleton("test"));
        message.setMetadata(Collections.singletonMap("tag", "value"));

        producer.send(message);

        final JmsConsumer consumer = new JmsConsumer(QUEUE);
        consumer.setConnectionPool(pool);
        consumer.setDeserializer(DefaultSerializerDeserializer.getInstance());

        MessageObject received = consumer.receive("test-queue-3", MessageObject.class);

        assertEquals(1, received.getId());
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

        final JmsProducer producer = new JmsProducer(QUEUE);
        producer.setConnectionPool(pool);
        producer.setSerializer(DefaultSerializerDeserializer.getInstance());

        for (int i = 2; i < 100; i++) {
            producer.send("test-queue-3", "test-message-" + i);
        }

        final CountDownLatch latch = new CountDownLatch(98);

        final JMSListenerContainer<String> listener = new JMSListenerContainer<>(pool, QUEUE);
        listener.setThreadPoolSize(10);
        listener.setMaxThreadPoolSize(20);
        listener.registerListener("test-queue-3", message -> {
            System.err.println("Received message " + message +
                " on thread " + Thread.currentThread().getName() +
                " at " + new Date());
            latch.countDown();
        }, String.class);

        latch.await(2L, SECONDS);

        assertEquals(0, latch.getCount());

        assertTrue(listener.shutdown(),
            "Failed to shutdown JMS listener. There are still open connections.");
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
        final JMSListenerContainerFactory listenerFactory =
            applicationContext.getBean(JMSListenerContainerFactory.class);
        assertNotNull(
            listenerFactory.getRegisteredListener("test-queue-2"),
            "Listener is null");

        final JmsProducer producer = new JmsProducer(QUEUE);
        producer.setConnectionPool(pool);
        producer.setSerializer(DefaultSerializerDeserializer.getInstance());

        for (int i = 0; i < 100; i++) {
            producer.send("test-queue-2", "test-message-" + i,
                new MessageHeader(JMS_CORRELATION_ID, "test-corr-id"),
                new MessageHeader("X-Arbitrary-Header", "arbitrary-value"));
        }

        QUEUE_LATCH.await(5L, SECONDS);

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
        final JMSListenerContainerFactory listenerFactory =
            applicationContext.getBean(JMSListenerContainerFactory.class);
        assertNotNull(
            listenerFactory.getRegisteredListener("my-topic"),
            "Listener is null");

        final JmsProducer producer = new JmsProducer(TOPIC);
        producer.setConnectionPool(pool);
        producer.setSerializer(DefaultSerializerDeserializer.getInstance());

        for (int i = 0; i < 100; i++) {
            producer.send("my-topic", "test-message-" + i,
                new MessageHeader(JMS_PRIORITY, Integer.toString(2)),
                new MessageHeader("X-Topic-Header", "arbitrary-value"));
        }

        TOPIC_LATCH.await(5L, SECONDS);

        assertEquals(0, TOPIC_LATCH.getCount());
    }

    @JMSListener("activeMqConnectionFactory")
    static class TestListener {
        @Queue(
            destination = "test-queue-2",
            concurrency = "1-5",
            transacted = true,
            acknowledgeMode = CLIENT_ACKNOWLEDGE)
        public void handle(@Body String message,
                           @Header(JMS_CORRELATION_ID) String correlationId,
                           @Header("X-Arbitrary-Header") @Nullable String arbitraryHeader,
                           @Header("X-Null-Header") @Nullable Integer nullHeader) {
            if ("test-corr-id".equals(correlationId) &&
                "arbitrary-value".equals(arbitraryHeader) &&
                nullHeader == null) {
                QUEUE_LATCH.countDown();
            }
            System.err.println("CorrelationID: " + correlationId);
            System.err.println("X-Arbitrary-Header: " + arbitraryHeader);
            System.err.println("Message: " + message);
        }

        @Topic(destination = "my-topic")
        public void receive(@Body String message,
                            @Header(JMS_PRIORITY) int priority,
                            @Header("X-Topic-Header") @Nullable String header) {
            if (priority == 2 && "arbitrary-value".equals(header)) {
                TOPIC_LATCH.countDown();
            }
            System.err.println("JMSPriority: " + priority);
            System.err.println("X-Topic-Header: " + header);
            System.err.println("Message: " + message);
        }
    }

    @JMSProducer("activeMqConnectionFactory")
    interface TestProducer {
        @Queue(
            destination = "test-queue-3",
            transacted = true,
            acknowledgeMode = CLIENT_ACKNOWLEDGE)
        void send(MessageObject object);
    }

    static class MessageObject implements Serializable {
        private Integer id;
        private String description;
        private boolean flag;
        private Collection<String> tags;
        private Map<String, String> metadata;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isFlag() {
            return flag;
        }

        public void setFlag(boolean flag) {
            this.flag = flag;
        }

        public Collection<String> getTags() {
            return tags;
        }

        public void setTags(Collection<String> tags) {
            this.tags = tags;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }
}
