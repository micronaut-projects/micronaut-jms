package io.micronaut.jms.listener;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionParameterTest {

    @Test
    void aParameterTypedAsAProviderSessionReceivesTheSessionOfTheListener() throws JMSException {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "SessionParameterTest"))) {
            StubBroker broker = context.getBean(StubBroker.class);
            ProviderSessionListener listener = context.getBean(ProviderSessionListener.class);

            broker.deliver("order-1");

            assertEquals(List.of("order-1"), listener.bodies);
            assertEquals(1, listener.sessions.size());
            assertSame(broker.session, listener.sessions.get(0));
        }
    }

    @Test
    void aSessionOfAnotherTypeFailsTheApplicationBeforeAnyMessageIsConsumed() {
        Exception e = assertThrows(Exception.class, () -> ApplicationContext.run(Map.of(
            "spec.name", "SessionParameterTest",
            "spec.listener", "other")).close());

        assertTrue(messages(e).contains("of the listener method OtherSessionListener#receive cannot be bound to the session"), messages(e));
    }

    @Test
    void anArgumentThatCannotBeBoundFailsTheApplicationAtStartup() {
        Exception e = assertThrows(Exception.class, () -> ApplicationContext.run(Map.of(
            "spec.name", "SessionParameterTest",
            "spec.listener", "unbound")).close());

        assertTrue(messages(e).contains("The argument 'note' of type java.lang.String of the @Queue listener method UnboundArgumentListener#receive cannot be bound"), messages(e));
    }

    @Test
    void aSessionAwareListenerCannotBeRegisteredWithAnExecutor() {
        StubBroker broker = new StubBroker();
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());
        SessionAwareMessageListener listener = (message, session) -> { };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> registry.register(broker.connection,
                JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE, listener, executor, true, Optional.empty()));

            assertTrue(e.getMessage().contains("cannot be registered with an executor"), e.getMessage());
            assertTrue(broker.sessionClosed, "the session of the rejected listener is closed");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void theListenerPassesItsSessionToASessionAwareListener() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());
        List<Session> sessions = new CopyOnWriteArrayList<>();
        SessionAwareMessageListener listener = (message, session) -> sessions.add(session);

        registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE,
            listener, null, true, Optional.empty());
        broker.deliver("order-1");

        assertEquals(List.of(broker.session), sessions);
        assertThrows(UnsupportedOperationException.class, () -> listener.onMessage(broker.message("order-2")));
        registry.shutdown();
    }

    @Test
    void aSessionTheListenerRejectsFailsTheRegistration() {
        StubBroker broker = new StubBroker();
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());
        SessionAwareMessageListener listener = new SessionAwareMessageListener() {
            @Override
            public void onMessage(Message message, Session session) {
                // never called
            }

            @Override
            public void checkSession(Session session) {
                throw new IllegalStateException("rejected");
            }
        };

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> registry.register(broker.connection,
            JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE, listener, null, true, Optional.empty()));

        assertEquals("rejected", e.getMessage());
        assertEquals(null, broker.consumerListener, "no consumer listens for messages");
    }

    private static String messages(Throwable e) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }

    /**
     * A provider session type, such as Oracle's {@code AQjmsSession}.
     */
    public interface ProviderSession extends Session {
    }

    /**
     * Another provider's session type, which the stub session does not implement.
     */
    public interface OtherSession extends Session {
    }

    /**
     * A broker of stubs: one connection and one session, whose consumer keeps its message listener.
     */
    @Singleton
    @Requires(property = "spec.name", value = "SessionParameterTest")
    static class StubBroker {

        final ProviderSession session;
        final Connection connection;
        final ConnectionFactory connectionFactory;
        volatile MessageListener consumerListener;
        volatile boolean sessionClosed;

        StubBroker() {
            MessageConsumer consumer = stub(MessageConsumer.class, (name, args) -> {
                if ("setMessageListener".equals(name)) {
                    consumerListener = (MessageListener) args[0];
                }
                return null;
            });
            jakarta.jms.Queue queue = stub(jakarta.jms.Queue.class, (name, args) -> null);
            session = stub(ProviderSession.class, (name, args) -> switch (name) {
                case "createQueue" -> queue;
                case "createConsumer" -> consumer;
                case "close" -> {
                    sessionClosed = true;
                    yield null;
                }
                default -> null;
            });
            connection = stub(Connection.class, (name, args) -> "createSession".equals(name) ? session : null);
            connectionFactory = stub(ConnectionFactory.class, (name, args) -> "createConnection".equals(name) ? connection : null);
        }

        void deliver(String body) {
            consumerListener.onMessage(message(body));
        }

        TextMessage message(String body) {
            return stub(TextMessage.class, (name, args) -> switch (name) {
                case "getText" -> body;
                case "getBody" -> body;
                case "isBodyAssignableTo" -> true;
                default -> null;
            });
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "SessionParameterTest")
    static class StubPoolFactory {

        @Singleton
        @Named("stub")
        JMSConnectionPool stubPool(StubBroker broker) {
            return new JMSConnectionPool(broker.connectionFactory, 0, 5);
        }
    }

    @JMSListener("stub")
    @Requires(property = "spec.name", value = "SessionParameterTest")
    @Requires(missingProperty = "spec.listener")
    static class ProviderSessionListener {

        final List<String> bodies = new CopyOnWriteArrayList<>();
        final List<Session> sessions = new CopyOnWriteArrayList<>();

        @Queue("orders")
        void receive(@MessageBody String body, ProviderSession session) {
            bodies.add(body);
            sessions.add(session);
        }
    }

    @JMSListener("stub")
    @Requires(property = "spec.listener", value = "unbound")
    static class UnboundArgumentListener {

        @Queue("orders")
        void receive(@MessageBody String body, String note) {
            // never called: note has no binding annotation
        }
    }

    @JMSListener("stub")
    @Requires(property = "spec.listener", value = "other")
    static class OtherSessionListener {

        @Queue("orders")
        void receive(@MessageBody String body, OtherSession session) {
            // never called: the session is not an OtherSession
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
