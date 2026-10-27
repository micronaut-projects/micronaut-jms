package io.micronaut.jms.templates;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.MessageDeliveryMode;
import io.micronaut.jms.annotations.MessagePriority;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.model.JMSDeliveryMode;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.messaging.exceptions.MessagingClientException;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryOptionsTest {

    private ApplicationContext context;
    private StubBroker broker;
    private AlertProducer producer;

    @BeforeEach
    void start() {
        context = ApplicationContext.run(Map.of("spec.name", "DeliveryOptionsTest"));
        broker = context.getBean(StubBroker.class);
        producer = context.getBean(AlertProducer.class);
    }

    @AfterEach
    void stop() {
        context.close();
    }

    @Test
    void producerMethodsSendPersistentMessagesWithTheDefaultPriority() {
        producer.send("alert");

        assertEquals(List.of(new Sent(DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY)), broker.sent);
    }

    @Test
    void aDeliveryModeArgumentCanBeAnEnumAnIntOrAName() {
        producer.sendWithMode("alert", JMSDeliveryMode.NON_PERSISTENT);
        producer.sendWithModeValue("alert", DeliveryMode.NON_PERSISTENT);
        producer.sendWithModeName("alert", "non_persistent");
        producer.sendWithModeName("alert", "2");

        assertEquals(List.of(
            new Sent(DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY),
            new Sent(DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY),
            new Sent(DeliveryMode.NON_PERSISTENT, Message.DEFAULT_PRIORITY),
            new Sent(DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY)), broker.sent);
    }

    @Test
    void theQueueAndTopicAnnotationsSetTheDeliveryModeAndPriorityThatArgumentsOverride() {
        producer.sendUrgent("alert");
        producer.sendUrgentWithPriority("alert", 2);
        producer.sendUrgentWithPriority("alert", null);
        producer.broadcast("alert");
        producer.broadcastWithMode("alert", JMSDeliveryMode.PERSISTENT);

        assertEquals(List.of(
            new Sent(DeliveryMode.NON_PERSISTENT, 8),
            new Sent(DeliveryMode.NON_PERSISTENT, 2),
            new Sent(DeliveryMode.NON_PERSISTENT, 8),
            new Sent(DeliveryMode.NON_PERSISTENT, 1),
            new Sent(DeliveryMode.PERSISTENT, 1)), broker.sent);
        assertEquals(List.of("alerts", "alerts", "alerts", "broadcasts", "broadcasts"), broker.destinations);
    }

    @Test
    void aPriorityArgumentCanBeAStringOfDigits() {
        producer.sendUrgentWithPriorityName("alert", " 7 ");

        assertEquals(List.of(new Sent(DeliveryMode.NON_PERSISTENT, 7)), broker.sent);
    }

    @Test
    void aPriorityArgumentOfAnotherTypeFailsTheCall() {
        MessagingClientException notDigits = assertThrows(MessagingClientException.class, () -> producer.sendUrgentWithPriorityName("alert", "high"));
        MessagingClientException otherType = assertThrows(MessagingClientException.class, () -> producer.sendUrgentWithPriorityObject("alert", true));

        assertTrue(notDigits.getMessage().contains("must be a number from 0 to 9, but was high"), notDigits.getMessage());
        assertTrue(otherType.getMessage().contains("must be a number or a String of digits, but was a java.lang.Boolean"), otherType.getMessage());
        assertEquals(List.of(), broker.sent, "nothing is sent, rather than the priority of the annotation");
    }

    @Test
    void invalidDeliveryOptionsFailTheCallWithAMessagingClientException() {
        MessagingClientException invalidMode = assertThrows(MessagingClientException.class, () -> producer.sendWithModeValue("alert", 3));
        MessagingClientException invalidName = assertThrows(MessagingClientException.class, () -> producer.sendWithModeName("alert", "SOMETIMES"));
        MessagingClientException invalidType = assertThrows(MessagingClientException.class, () -> producer.sendWithModeObject("alert", true));
        MessagingClientException invalidPriority = assertThrows(MessagingClientException.class, () -> producer.sendWithPriority("alert", 10));
        MessagingClientException negativePriority = assertThrows(MessagingClientException.class, () -> producer.sendWithPriority("alert", -1));
        MessagingClientException blankName = assertThrows(MessagingClientException.class, () -> producer.sendWithModeName("alert", " "));

        assertTrue(invalidMode.getMessage().contains("No JMSDeliveryMode defined for 3"), invalidMode.getMessage());
        assertTrue(invalidName.getMessage().contains("expected NON_PERSISTENT or PERSISTENT"), invalidName.getMessage());
        assertTrue(invalidType.getMessage().contains("must be a JMSDeliveryMode, a jakarta.jms.DeliveryMode int or the name"), invalidType.getMessage());
        assertInstanceOf(IllegalArgumentException.class, invalidPriority.getCause());
        assertInstanceOf(IllegalArgumentException.class, negativePriority.getCause());
        assertTrue(blankName.getMessage().contains("expected NON_PERSISTENT or PERSISTENT"), blankName.getMessage());
        assertEquals(List.of(), broker.sent, "nothing is sent");
    }

    @Test
    void theProducerSendsSerializedBodiesWithTheDefaultPriorityAndMessagesWithTheirOwn() throws JMSException {
        JmsProducer<Object> jmsProducer = new JmsProducer<>(JMSDestinationType.QUEUE, context.getBean(JMSConnectionPool.class),
            context.getBean(DefaultSerializerDeserializer.class));
        Message message = broker.textMessage("alert", 6);

        jmsProducer.send("alerts", "alert", 0L);
        jmsProducer.send("alerts", message, 0L);
        jmsProducer.send(broker.queue, message, 0L);
        jmsProducer.send("alerts", "alert", JMSDeliveryMode.NON_PERSISTENT, 9, 0L);

        assertEquals(List.of(
            new Sent(DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY),
            new Sent(DeliveryMode.PERSISTENT, 6),
            new Sent(DeliveryMode.PERSISTENT, 6),
            new Sent(DeliveryMode.NON_PERSISTENT, 9)), broker.sent);
    }

    record Sent(int deliveryMode, int priority) {
    }

    @JMSProducer("stub")
    @Requires(property = "spec.name", value = "DeliveryOptionsTest")
    interface AlertProducer {

        @Queue("alerts")
        void send(@MessageBody String body);

        @Queue("alerts")
        void sendWithMode(@MessageBody String body, @MessageDeliveryMode JMSDeliveryMode mode);

        @Queue("alerts")
        void sendWithModeValue(@MessageBody String body, @MessageDeliveryMode int mode);

        @Queue("alerts")
        void sendWithModeName(@MessageBody String body, @MessageDeliveryMode String mode);

        @Queue("alerts")
        void sendWithModeObject(@MessageBody String body, @MessageDeliveryMode Object mode);

        @Queue("alerts")
        void sendWithPriority(@MessageBody String body, @MessagePriority int priority);

        @Queue(value = "alerts", deliveryMode = JMSDeliveryMode.NON_PERSISTENT, priority = 8)
        void sendUrgent(@MessageBody String body);

        @Queue(value = "alerts", deliveryMode = JMSDeliveryMode.NON_PERSISTENT, priority = 8)
        void sendUrgentWithPriority(@MessageBody String body, @MessagePriority Integer priority);

        @Queue(value = "alerts", deliveryMode = JMSDeliveryMode.NON_PERSISTENT, priority = 8)
        void sendUrgentWithPriorityName(@MessageBody String body, @MessagePriority String priority);

        @Queue(value = "alerts", deliveryMode = JMSDeliveryMode.NON_PERSISTENT, priority = 8)
        void sendUrgentWithPriorityObject(@MessageBody String body, @MessagePriority Object priority);

        @Topic(value = "broadcasts", deliveryMode = JMSDeliveryMode.NON_PERSISTENT, priority = 1)
        void broadcast(@MessageBody String body);

        @Topic(value = "broadcasts", deliveryMode = JMSDeliveryMode.NON_PERSISTENT, priority = 1)
        void broadcastWithMode(@MessageBody String body, @MessageDeliveryMode JMSDeliveryMode mode);
    }

    /**
     * One connection and session, whose producers record the delivery mode and priority of each message they send.
     * Like ActiveMQ Classic, a new message has priority 0 until it is sent.
     */
    @Singleton
    @Requires(property = "spec.name", value = "DeliveryOptionsTest")
    static class StubBroker {

        final List<Sent> sent = new CopyOnWriteArrayList<>();
        final List<String> destinations = new CopyOnWriteArrayList<>();
        final jakarta.jms.Queue queue = stub(jakarta.jms.Queue.class, (name, args) -> null);
        final jakarta.jms.Topic topic = stub(jakarta.jms.Topic.class, (name, args) -> null);
        final ConnectionFactory connectionFactory;

        StubBroker() {
            Session session = stub(Session.class, (name, args) -> switch (name) {
                case "createQueue" -> {
                    destinations.add((String) args[0]);
                    yield queue;
                }
                case "createTopic" -> {
                    destinations.add((String) args[0]);
                    yield topic;
                }
                case "createProducer" -> stub(MessageProducer.class, (method, sendArgs) -> {
                    if ("send".equals(method) && sendArgs.length == 4) {
                        sent.add(new Sent((int) sendArgs[1], (int) sendArgs[2]));
                    }
                    return null;
                });
                case "createTextMessage" -> textMessage((String) args[0], 0);
                default -> null;
            });
            Connection connection = stub(Connection.class, (name, args) -> "createSession".equals(name) ? session : null);
            connectionFactory = stub(ConnectionFactory.class, (name, args) -> "createConnection".equals(name) ? connection : null);
        }

        TextMessage textMessage(String text, int priority) {
            return stub(TextMessage.class, (name, args) -> switch (name) {
                case "getText" -> text;
                case "getJMSPriority" -> priority;
                default -> null;
            });
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "DeliveryOptionsTest")
    static class StubPoolFactory {

        @Singleton
        @Named("stub")
        JMSConnectionPool stubPool(StubBroker broker) {
            return new JMSConnectionPool(broker.connectionFactory, 0, 5);
        }
    }

    private static <T> T stub(Class<T> type, Answer answer) {
        Object stub = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object result = switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> type.getSimpleName() + " stub";
                default -> answer.answer(method.getName(), args == null ? new Object[0] : args);
            };
            if (result == null && method.getReturnType().isPrimitive()) {
                return defaultValue(method.getReturnType());
            }
            return result;
        });
        return type.cast(stub);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }

    @FunctionalInterface
    private interface Answer {
        Object answer(String method, Object[] args) throws JMSException;
    }
}
