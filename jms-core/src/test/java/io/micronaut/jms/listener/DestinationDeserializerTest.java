package io.micronaut.jms.listener;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.serdes.Deserializer;
import io.micronaut.core.annotation.Order;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.messaging.annotation.MessageHeader;
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
import java.util.Collections;
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
    void theDeserializerOfATopicConvertsTheBodyAndHeadersAreBoundByTheDefaultBinders() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "DestinationDeserializerTest"))) {
            StubBroker broker = context.getBean(StubBroker.class);
            TagListener listener = context.getBean(TagListener.class);

            broker.deliver("tag-events", "blue,amber", Map.of("tenant", "acme"));

            assertEquals(List.of(List.of("BLUE", "AMBER")), listener.tagEvents);
            assertEquals(List.of("acme"), listener.tenants);
        }
    }

    @Test
    void aPrimaryDeserializerStillReplacesTheGlobalOne() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "DestinationDeserializerTest",
            "spec.global", "primary"))) {
            StubBroker broker = context.getBean(StubBroker.class);
            TagListener listener = context.getBean(TagListener.class);

            broker.deliver("names", "alice");
            broker.deliver("tags", "red");

            assertEquals(List.of("primary:alice"), listener.names);
            assertEquals(List.of(List.of("RED")), listener.tags, "a named deserializer is still used for its destination");
        }
    }

    @Test
    void aDeserializerThatReplacesTheDefaultIsTheGlobalOneNextToANamedOne() {
        for (String replacing : List.of("default", "interface")) {
            try (ApplicationContext context = ApplicationContext.run(Map.of(
                "spec.name", "DestinationDeserializerTest",
                "spec.global", "replaces-" + replacing))) {
                StubBroker broker = context.getBean(StubBroker.class);
                TagListener listener = context.getBean(TagListener.class);

                broker.deliver("names", "alice");
                broker.deliver("tags", "red");

                assertEquals(List.of("replaced-" + replacing + ":alice"), listener.names);
                assertEquals(List.of(List.of("RED")), listener.tags, "the named deserializer is still used for its destination");
            }
        }
    }

    @Test
    void aDeserializerWithAHigherPrecedenceOrderStillReplacesTheGlobalOne() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "DestinationDeserializerTest",
            "spec.global", "ordered"))) {
            StubBroker broker = context.getBean(StubBroker.class);
            TagListener listener = context.getBean(TagListener.class);

            broker.deliver("names", "alice");

            assertEquals(List.of("ordered:alice"), listener.names);
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
     * Replaces the global deserializer as a primary bean, as applications did before named deserializers.
     */
    @Singleton
    @Primary
    @Requires(property = "spec.global", value = "primary")
    static class PrimaryDeserializer implements Deserializer {

        @Override
        public <T> T deserialize(Message message, Class<T> clazz) {
            return clazz.cast("primary:" + text(message));
        }
    }

    /**
     * Replaces the global deserializer with a higher precedence order.
     */
    @Singleton
    @Order(-100)
    @Requires(property = "spec.global", value = "ordered")
    static class OrderedDeserializer implements Deserializer {

        @Override
        public <T> T deserialize(Message message, Class<T> clazz) {
            return clazz.cast("ordered:" + text(message));
        }
    }

    /**
     * Replaces the default deserializer.
     */
    @Singleton
    @Replaces(DefaultSerializerDeserializer.class)
    @Requires(property = "spec.global", value = "replaces-default")
    static class ReplacingDeserializer implements Deserializer {

        @Override
        public <T> T deserialize(Message message, Class<T> clazz) {
            return clazz.cast("replaced-default:" + text(message));
        }
    }

    /**
     * Replaces the deserializer interface, which its default implementation makes a replacement of the default.
     */
    @Singleton
    @Replaces(Deserializer.class)
    @Requires(property = "spec.global", value = "replaces-interface")
    static class InterfaceReplacingDeserializer implements Deserializer {

        @Override
        public <T> T deserialize(Message message, Class<T> clazz) {
            return clazz.cast("replaced-interface:" + text(message));
        }
    }

    private static String text(Message message) {
        try {
            return ((TextMessage) message).getText();
        } catch (JMSException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A broker of stubs: one connection and one session, whose consumers keep their message listeners by queue or
     * topic name.
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
                case "createTopic" -> {
                    String topicName = (String) args[0];
                    yield stub(jakarta.jms.Topic.class, (method, topicArgs) -> "getTopicName".equals(method) ? topicName : null);
                }
                case "createConsumer" -> {
                    String destinationName = args[0] instanceof jakarta.jms.Topic topic
                        ? topic.getTopicName()
                        : ((jakarta.jms.Queue) args[0]).getQueueName();
                    yield stub(MessageConsumer.class, (method, consumerArgs) -> {
                        if ("setMessageListener".equals(method)) {
                            listeners.put(destinationName, (MessageListener) consumerArgs[0]);
                        }
                        return null;
                    });
                }
                default -> null;
            });
            Connection connection = stub(Connection.class, (name, args) -> "createSession".equals(name) ? session : null);
            connectionFactory = stub(ConnectionFactory.class, (name, args) -> "createConnection".equals(name) ? connection : null);
        }

        TextMessage deliver(String destination, String body) {
            return deliver(destination, body, Map.of());
        }

        TextMessage deliver(String destination, String body, Map<String, String> properties) {
            TextMessage message = stub(TextMessage.class, (name, args) -> switch (name) {
                case "getText", "getBody" -> body;
                case "isBodyAssignableTo" -> true;
                case "getObjectProperty", "getStringProperty" -> properties.get((String) args[0]);
                case "propertyExists" -> properties.containsKey((String) args[0]);
                case "getPropertyNames" -> Collections.enumeration(properties.keySet());
                default -> null;
            });
            listeners.get(destination).onMessage(message);
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
        final List<List<?>> tagEvents = new CopyOnWriteArrayList<>();
        final List<String> tenants = new CopyOnWriteArrayList<>();

        @Queue(value = "tags", deserializer = "tags")
        void receiveTags(@MessageBody List<?> body, @io.micronaut.jms.annotations.Message Message message) {
            tags.add(body);
            messages.add(message);
        }

        @Queue("names")
        void receiveName(@MessageBody String body) {
            names.add(body);
        }

        @Topic(value = "tag-events", deserializer = "tags")
        void receiveTagEvent(@MessageBody List<?> body, @MessageHeader("tenant") String tenant) {
            tagEvents.add(body);
            tenants.add(tenant);
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
