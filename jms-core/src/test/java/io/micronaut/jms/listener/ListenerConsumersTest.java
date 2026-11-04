package io.micronaut.jms.listener;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenerConsumersTest {

    private static final String SPEC = "ListenerConsumersTest";

    @Test
    void theConsumersShareOnePooledConnectionEachWithItsOwnSession() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSConnectionPool pool = new JMSConnectionPool(broker.connectionFactory, 0, 1);
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());

        List<io.micronaut.jms.listener.JMSListener> listeners = registerQueue(registry, pool, 3);

        assertEquals(3, listeners.size());
        assertEquals(1, broker.connections.size(), "one connection for all consumers");
        assertEquals(3, broker.sessions.size());
        assertEquals(3, identityCount(broker.sessions), "each consumer has its own session");
        assertThrows(IllegalStateException.class, pool::createConnection, "the consumers take a single pool slot");

        registry.shutdown();

        assertEquals(3, broker.calls.stream().filter("session.close"::equals).count());
        assertNotNull(pool.createConnection(), "the connection is returned on shutdown");
        assertThrows(IllegalStateException.class, pool::createConnection, "and returned once");
    }

    @Test
    void theConsumersOfASubscriptionWithAClientIdShareOneDedicatedConnection() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSConnectionPool pool = new JMSConnectionPool(broker.connectionFactory, 0, 1);
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());

        registry.register(pool, JMSDestinationType.TOPIC, "orders", false, Session.AUTO_ACKNOWLEDGE, message -> { },
            null, true, Optional.empty(), new JMSTopicSubscription("workers", false, true, "app-1"), 2);

        assertEquals(1, broker.connections.size());
        assertEquals(List.of("connection.setClientID app-1"), broker.calls.stream().filter(call -> call.startsWith("connection.setClientID")).toList());
        assertEquals(2, broker.calls.stream().filter("createSharedConsumer workers"::equals).count());
        assertNotNull(assertDoesNotThrow(() -> pool.createConnection()), "a dedicated connection takes no pool slot");

        registry.shutdown();

        assertEquals(1, broker.calls.stream().filter("connection.close"::equals).count(), "the shared connection is closed once");
    }

    @Test
    void registrationStopsAtTheFirstFailingConsumerAndReturnsTheConnection() throws JMSException {
        StubBroker broker = new StubBroker();
        broker.failingConsumer = 2;
        JMSConnectionPool pool = new JMSConnectionPool(broker.connectionFactory, 0, 1);
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());

        JMSException e = assertThrows(JMSException.class, () -> registerQueue(registry, pool, 3));

        assertEquals("consumer 2 failed", e.getMessage());
        assertEquals(2, broker.calls.stream().filter("createConsumer"::equals).count(), "no consumer after the failing one");
        assertEquals(2, broker.calls.stream().filter("session.close"::equals).count(), "both sessions are closed");
        assertTrue(broker.calls.contains("consumer.close"), "the consumer that started is stopped");
        assertNotNull(pool.createConnection(), "the connection is returned to the pool");

        registry.shutdown();
        assertEquals(1, broker.calls.stream().filter("consumer.close"::equals).count(), "the stopped consumer is not stopped again");
    }

    @Test
    void aSingleConsumerThatFailsReturnsItsPooledConnection() throws JMSException {
        StubBroker broker = new StubBroker();
        broker.failingConsumer = 1;
        JMSConnectionPool pool = new JMSConnectionPool(broker.connectionFactory, 0, 1);
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());

        for (int i = 0; i < 3; i++) {
            assertThrows(JMSException.class, () -> registry.register(pool, JMSDestinationType.QUEUE, "jobs", false,
                Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.empty(), null));
        }

        assertNotNull(pool.createConnection(), "every failed registration returned the connection");
    }

    @Test
    void aPooledConnectionStaysOutOfThePoolWhenAConsumerFailsToStop() {
        StubBroker broker = new StubBroker();
        broker.failingConsumer = 2;
        broker.sessionCloseFailure = new JMSException("session gone");
        JMSConnectionPool pool = new JMSConnectionPool(broker.connectionFactory, 0, 1);
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());

        JMSException e = assertThrows(JMSException.class, () -> registerQueue(registry, pool, 2));

        assertTrue(List.of(e.getSuppressed()).contains(broker.sessionCloseFailure));
        assertThrows(IllegalStateException.class, pool::createConnection, "it may still hold the consumer's session");
    }

    @Test
    void aDedicatedConnectionIsClosedWhenAConsumerFails() {
        StubBroker broker = new StubBroker();
        broker.failingConsumer = 2;
        broker.sessionCloseFailure = new JMSException("session gone");
        JMSConnectionPool pool = new JMSConnectionPool(broker.connectionFactory, 0, 1);
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());

        assertThrows(JMSException.class, () -> registry.register(pool, JMSDestinationType.TOPIC, "orders", false,
            Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.empty(), new JMSTopicSubscription("workers", true, true, "app-1"), 2));

        assertEquals(1, broker.calls.stream().filter("connection.close"::equals).count());
    }

    @Test
    void aDedicatedConnectionIsClosedWhenItsClientIdCannotBeSet() {
        StubBroker broker = new StubBroker();
        broker.clientIdFailure = new JMSException("client ID in use");
        broker.connectionCloseFailure = new JMSException("broker gone");
        JMSConnectionPool pool = new JMSConnectionPool(broker.connectionFactory, 0, 1);
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());

        JMSException e = assertThrows(JMSException.class, () -> registry.register(pool, JMSDestinationType.TOPIC, "orders", false,
            Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.empty(), new JMSTopicSubscription("audit", true, false, null), 1));

        assertSame(broker.clientIdFailure, e);
        assertSame(broker.connectionCloseFailure, e.getSuppressed()[0]);
        assertTrue(broker.sessions.isEmpty());
    }

    @Test
    void theRegistryRequiresAtLeastOneConsumer() {
        StubBroker broker = new StubBroker();
        JMSConnectionPool pool = new JMSConnectionPool(broker.connectionFactory, 0, 1);
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> registerQueue(registry, pool, 0));

        assertTrue(e.getMessage().contains("must have at least one consumer"), e.getMessage());
        assertTrue(broker.connections.isEmpty());
    }

    @Test
    void aListenerMethodWithSeveralConsumersTakesOnePoolSlot() throws JMSException {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC, "spec.listener", "jobs"))) {
            StubBroker broker = context.getBean(StubBroker.class);
            JobListener listener = context.getBean(JobListener.class);
            JMSConnectionPool pool = context.getBean(JMSConnectionPool.class);

            assertEquals(1, broker.connections.size());
            assertEquals(3, identityCount(broker.sessions));
            assertTrue(broker.calls.contains("createSession true 1"), "the consumers' sessions are transacted");
            assertThrows(IllegalStateException.class, pool::createConnection, "the pool of one is taken by the listener");

            broker.deliverToEveryConsumer("job");

            assertEquals(3, listener.sessions.size(), "every consumer calls the listener with its own session");
            assertEquals(3, broker.calls.stream().filter("session.commit"::equals).count());
        }
    }

    @Test
    void aSharedSubscriptionWithAClientIdCanHaveSeveralConsumers() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC, "spec.listener", "shared"))) {
            StubBroker broker = context.getBean(StubBroker.class);

            assertEquals(2, broker.calls.stream().filter("createSharedDurableConsumer audit"::equals).count());
            assertEquals(1, broker.connections.size());
        }
    }

    @Test
    void aListenerMethodWhoseConsumerFailsIsNotRegisteredAndReturnsItsConnection() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC, "spec.listener", "jobs", "spec.failing-consumer", 2))) {
            StubBroker broker = context.getBean(StubBroker.class);
            JMSConnectionPool pool = context.getBean(JMSConnectionPool.class);

            assertEquals(2, broker.calls.stream().filter("createConsumer"::equals).count(), "registration stops at the failing consumer");
            assertNotNull(assertDoesNotThrow(() -> pool.createConnection()), "the connection is back in the pool");
        }
    }

    @Test
    void aTransactedProducerMethodCommitsItsSend() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC))) {
            StubBroker broker = context.getBean(StubBroker.class);
            JobProducer producer = context.getBean(JobProducer.class);

            producer.send("job-1");
            producer.sendTransacted("job-2");

            assertEquals(List.of("createSession false 1", "send", "createSession true 0", "send", "session.commit"),
                broker.calls.stream().filter(call -> call.startsWith("createSession") || call.equals("send") || call.equals("session.commit")).toList());
        }
    }

    @Test
    void invalidConsumersFailTheStartupWithTheReason() {
        assertStartupFails("none", "The listener of none must have at least one consumer, but consumers = 0");
        assertStartupFails("plain-topic", "The listener of topic news has no subscription, so every consumer would receive every message:"
            + " more than one consumer requires a shared subscription");
        assertStartupFails("unshared-durable", "The subscription 'audit-log' of topic audit is an unshared durable subscription,"
            + " which allows only one consumer: more than one consumer requires a shared subscription");
    }

    private static void assertStartupFails(String listener, String message) {
        Exception e = assertThrows(Exception.class, () -> ApplicationContext.run(Map.of("spec.name", SPEC, "spec.listener", listener)).close());
        StringBuilder messages = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        assertTrue(messages.toString().contains(message), messages.toString());
    }

    private static List<io.micronaut.jms.listener.JMSListener> registerQueue(JMSListenerRegistry registry, JMSConnectionPool pool, int consumers)
        throws JMSException {
        return registry.register(pool, JMSDestinationType.QUEUE, "jobs", false, Session.AUTO_ACKNOWLEDGE, message -> { },
            null, true, Optional.empty(), null, consumers);
    }

    private static int identityCount(List<?> objects) {
        Set<Object> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.addAll(objects);
        return distinct.size();
    }

    @JMSListener("stub")
    @Requires(property = "spec.listener", value = "jobs")
    static class JobListener {

        final Set<Session> sessions = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

        @Queue(value = "jobs", consumers = 3, transacted = true)
        void process(@MessageBody String job, Session session) {
            sessions.add(session);
        }
    }

    @JMSListener("stub")
    @Requires(property = "spec.listener", value = "shared")
    static class SharedSubscriptionListener {

        @Topic(value = "audit", subscription = "audit", durable = true, shared = true, clientId = "audit-app", consumers = 2)
        void receive(@MessageBody String body) {
            // messages are not delivered in this test
        }
    }

    @JMSListener("stub")
    @Requires(property = "spec.listener", value = "none")
    static class NoConsumers {

        @Queue(value = "none", consumers = 0)
        void receive(@MessageBody String body) {
            // never registered
        }
    }

    @JMSListener("stub")
    @Requires(property = "spec.listener", value = "plain-topic")
    static class PlainTopicConsumers {

        @Topic(value = "news", consumers = 2)
        void receive(@MessageBody String body) {
            // never registered
        }
    }

    @JMSListener("stub")
    @Requires(property = "spec.listener", value = "unshared-durable")
    static class UnsharedDurableConsumers {

        @Topic(value = "audit", subscription = "audit-log", durable = true, consumers = 2)
        void receive(@MessageBody String body) {
            // never registered
        }
    }

    @JMSProducer("stub")
    @Requires(property = "spec.name", value = SPEC)
    interface JobProducer {

        @Queue("jobs")
        void send(@MessageBody String body);

        @Queue(value = "jobs", transacted = true)
        void sendTransacted(@MessageBody String body);
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC)
    static class StubPoolFactory {

        @Singleton
        StubBroker broker(io.micronaut.context.env.Environment environment) {
            StubBroker broker = new StubBroker();
            broker.failingConsumer = environment.getProperty("spec.failing-consumer", Integer.class).orElse(0);
            return broker;
        }

        @Singleton
        @Named("stub")
        JMSConnectionPool stubPool(StubBroker broker) {
            return new JMSConnectionPool(broker.connectionFactory, 0, 1);
        }
    }

    /**
     * A broker of stubs: a new connection per factory call and a new session per connection call. Records the
     * calls, keeps the message listener of each consumer and can fail every consumer from the n-th on.
     */
    static class StubBroker {

        final List<String> calls = new CopyOnWriteArrayList<>();
        final List<Connection> connections = new CopyOnWriteArrayList<>();
        final List<Session> sessions = new CopyOnWriteArrayList<>();
        final Map<Session, MessageListener> consumerListeners = Collections.synchronizedMap(new IdentityHashMap<>());
        final ConnectionFactory connectionFactory;
        volatile int failingConsumer;
        volatile JMSException sessionCloseFailure;
        volatile JMSException clientIdFailure;
        volatile JMSException connectionCloseFailure;
        private int consumersCreated;

        StubBroker() {
            connectionFactory = stub(ConnectionFactory.class, (name, args) -> {
                if (!"createConnection".equals(name)) {
                    return null;
                }
                Connection connection = connection();
                connections.add(connection);
                return connection;
            });
        }

        void deliverToEveryConsumer(String body) {
            new HashMap<>(consumerListeners).values().forEach(listener -> listener.onMessage(message(body)));
        }

        private Connection connection() {
            return stub(Connection.class, (name, args) -> switch (name) {
                case "createSession" -> {
                    calls.add("createSession " + args[0] + " " + args[1]);
                    Session session = session();
                    sessions.add(session);
                    yield session;
                }
                case "setClientID" -> {
                    calls.add("connection.setClientID " + args[0]);
                    if (clientIdFailure != null) {
                        throw clientIdFailure;
                    }
                    yield null;
                }
                case "close" -> {
                    calls.add("connection.close");
                    if (connectionCloseFailure != null) {
                        throw connectionCloseFailure;
                    }
                    yield null;
                }
                default -> null;
            });
        }

        private Session session() {
            jakarta.jms.Queue queue = stub(jakarta.jms.Queue.class, (name, args) -> null);
            jakarta.jms.Topic topic = stub(jakarta.jms.Topic.class, (name, args) -> null);
            MessageProducer producer = stub(MessageProducer.class, (name, args) -> {
                if ("send".equals(name)) {
                    calls.add("send");
                }
                return null;
            });
            Session[] self = new Session[1];
            self[0] = stub(Session.class, (name, args) -> switch (name) {
                case "createQueue" -> queue;
                case "createTopic" -> topic;
                case "createProducer" -> producer;
                case "createTextMessage" -> message((String) args[0]);
                case "createConsumer" -> {
                    calls.add("createConsumer");
                    yield consumer(self[0]);
                }
                case "createSharedConsumer", "createSharedDurableConsumer" -> {
                    calls.add(name + " " + args[1]);
                    yield consumer(self[0]);
                }
                case "commit" -> {
                    calls.add("session.commit");
                    yield null;
                }
                case "close" -> {
                    calls.add("session.close");
                    if (sessionCloseFailure != null) {
                        throw sessionCloseFailure;
                    }
                    yield null;
                }
                default -> null;
            });
            return self[0];
        }

        private synchronized MessageConsumer consumer(Session session) throws JMSException {
            consumersCreated++;
            if (failingConsumer > 0 && consumersCreated >= failingConsumer) {
                throw new JMSException("consumer " + consumersCreated + " failed");
            }
            return stub(MessageConsumer.class, (name, args) -> {
                if ("setMessageListener".equals(name)) {
                    consumerListeners.put(session, (MessageListener) args[0]);
                } else if ("close".equals(name)) {
                    calls.add("consumer.close");
                }
                return null;
            });
        }

        TextMessage message(String body) {
            return stub(TextMessage.class, (name, args) -> switch (name) {
                case "getText", "getBody" -> body;
                case "isBodyAssignableTo" -> true;
                default -> null;
            });
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
