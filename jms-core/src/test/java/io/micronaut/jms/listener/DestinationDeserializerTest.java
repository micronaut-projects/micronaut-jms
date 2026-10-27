package io.micronaut.jms.listener;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.Deserializer;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestinationDeserializerTest {

    @Test
    void theDeserializerOfAQueueConvertsTheBodyAndTheOtherArgumentsAreBoundAsUsual() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "DestinationDeserializerTest"))) {
            StubBroker broker = context.getBean(StubBroker.class);
            TagListener listener = context.getBean(TagListener.class);

            TextMessage tags = broker.deliver("tags", "red,green");
            broker.deliver("names", "alice");

            assertEquals(List.of(List.of("RED", "GREEN")), listener.tags);
            assertEquals(1, listener.messages.size());
            assertSame(tags, listener.messages.get(0));
            assertEquals(List.of("alice"), listener.names, "a queue without a deserializer uses the default one");
        }
    }

    @Test
    void aDeserializerThatIsNotABeanFailsTheApplication() {
        Exception e = assertThrows(Exception.class, () -> ApplicationContext.run(Map.of(
            "spec.name", "DestinationDeserializerTest",
            "spec.listener", "unknown")).close());

        assertTrue(messages(e).contains("missing"), messages(e));
    }

    private static String messages(Throwable e) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }

    /**
     * Converts a comma-separated text message into a list of upper-case tags.
     */
    @Singleton
    @Named("tags")
    @Requires(property = "spec.name", value = "DestinationDeserializerTest")
    static class TagDeserializer implements Deserializer {

        @Override
        public <T> T deserialize(Message message, Class<T> clazz) {
            try {
                String text = ((TextMessage) message).getText();
                return clazz.cast(List.of(text.toUpperCase(Locale.ROOT).split(",")));
            } catch (JMSException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * A broker of stubs: one connection and one session, whose consumers keep their message listeners by queue.
     */
    @Singleton
    @Requires(property = "spec.name", value = "DestinationDeserializerTest")
    static class StubBroker {

        final Map<String, MessageListener> listeners = new ConcurrentHashMap<>();
        final ConnectionFactory connectionFactory;

        StubBroker() {
            Session session = stub(Session.class, (name, args) -> switch (name) {
                case "createQueue" -> {
                    String queueName = (String) args[0];
                    yield stub(jakarta.jms.Queue.class, (method, queueArgs) -> "getQueueName".equals(method) ? queueName : null);
                }
                case "createConsumer" -> {
                    String queueName = ((jakarta.jms.Queue) args[0]).getQueueName();
                    yield stub(MessageConsumer.class, (method, consumerArgs) -> {
                        if ("setMessageListener".equals(method)) {
                            listeners.put(queueName, (MessageListener) consumerArgs[0]);
                        }
                        return null;
                    });
                }
                default -> null;
            });
            Connection connection = stub(Connection.class, (name, args) -> "createSession".equals(name) ? session : null);
            connectionFactory = stub(ConnectionFactory.class, (name, args) -> "createConnection".equals(name) ? connection : null);
        }

        TextMessage deliver(String queue, String body) {
            TextMessage message = stub(TextMessage.class, (name, args) -> switch (name) {
                case "getText", "getBody" -> body;
                case "isBodyAssignableTo" -> true;
                default -> null;
            });
            listeners.get(queue).onMessage(message);
            return message;
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "DestinationDeserializerTest")
    static class StubPoolFactory {

        @Singleton
        @Named("stub")
        JMSConnectionPool stubPool(StubBroker broker) {
            return new JMSConnectionPool(broker.connectionFactory, 0, 5);
        }
    }

    @JMSListener("stub")
    @Requires(property = "spec.name", value = "DestinationDeserializerTest")
    @Requires(missingProperty = "spec.listener")
    static class TagListener {

        final List<List<?>> tags = new CopyOnWriteArrayList<>();
        final List<Message> messages = new CopyOnWriteArrayList<>();
        final List<String> names = new CopyOnWriteArrayList<>();

        @Queue(value = "tags", deserializer = "tags")
        void receiveTags(@MessageBody List<?> body, @io.micronaut.jms.annotations.Message Message message) {
            tags.add(body);
            messages.add(message);
        }

        @Queue("names")
        void receiveName(@MessageBody String body) {
            names.add(body);
        }
    }

    @JMSListener("stub")
    @Requires(property = "spec.listener", value = "unknown")
    static class UnknownDeserializerListener {

        @Queue(value = "tags", deserializer = "missing")
        void receive(@MessageBody String body) {
            // never called: there is no deserializer named missing
        }
    }

    private static <T> T stub(Class<T> type, Answer answer) {
        Object stub = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object result = switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> type.getSimpleName() + " stub";
                default -> answer.answer(method.getName(), args);
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
