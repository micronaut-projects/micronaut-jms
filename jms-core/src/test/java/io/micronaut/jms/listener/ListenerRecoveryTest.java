package io.micronaut.jms.listener;

import io.micronaut.context.ApplicationContext;
import io.micronaut.jms.configuration.properties.JMSListenerRecoveryConfiguration;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import jakarta.jms.TopicSubscriber;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListenerRecoveryTest {

    private static final JMSException SESSION_DIED = new JMSException("JMS-120: Dequeue failed");

    @Test
    void aConsumerWhoseSessionFailedRecreatesItsSessionAndConsumerOnTheSameConnection() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSConnectionPool pool = new JMSConnectionPool(broker.connectionFactory, 0, 1);
        JMSListenerRegistry registry = registry(recovery(true, 3, Duration.ofMillis(1)));
        List<Session> handledWith = new CopyOnWriteArrayList<>();
        SessionAwareMessageListener delegate = (message, session) -> handledWith.add(session);

        registry.register(pool, JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE, delegate,
            null, true, Optional.empty(), null, 2);
        StubSession failed = broker.sessions.get(0);
        StubSession working = broker.sessions.get(1);
        failed.broken = true;
        broker.reportError(SESSION_DIED);

        await(() -> broker.sessions.size() == 3 && broker.sessions.get(2).consumers.size() == 1);
        StubSession recreated = broker.sessions.get(2);
        assertTrue(failed.closed, "the failed session is closed");
        assertTrue(failed.consumers.get(0).closed, "the failed consumer is closed");
        assertFalse(working.closed, "a consumer whose session works keeps it");
        assertEquals(1, broker.connections.size(), "the session is recreated on the same connection");
        assertEquals(List.of("orders"), recreated.queues);

        recreated.consumers.get(0).deliver(message());
        assertEquals(List.of(recreated.session), handledWith, "the message is handled with the recreated session");
        registry.shutdown();
        assertTrue(recreated.closed);
    }

    @Test
    void anErrorThatLeavesTheSessionUsableDoesNotRecreateIt() throws JMSException {
        StubBroker broker = new StubBroker();
        List<JMSException> forwarded = new CopyOnWriteArrayList<>();
        ExceptionListener previous = forwarded::add;
        broker.exceptionListener = previous;
        JMSListenerRegistry registry = registry(recovery(true, 3, Duration.ofMillis(1)));
        JMSListener listener = registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false,
            Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.empty(), null);
        // for example ActiveMQ Classic, which reports a rejected asynchronous send while the connection still works
        JMSException rejectedSend = new JMSException("Rejected asynchronous send");

        broker.reportError(rejectedSend);
        registry.recover(listener, rejectedSend, 1);

        assertEquals(List.of(rejectedSend), forwarded, "the error still reaches the exception listener set before");
        assertEquals(1, broker.sessions.size(), "the session is not recreated");
        assertFalse(broker.sessions.get(0).closed);
        registry.shutdown();
    }

    @Test
    void theRegistryGivesTheConnectionItsPreviousExceptionListenerBackOnShutdown() throws JMSException {
        StubBroker broker = new StubBroker();
        ExceptionListener previous = e -> { };
        broker.exceptionListener = previous;
        JMSListenerRegistry registry = registry(recovery(true, 3, Duration.ofMillis(1)));
        registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE,
            message -> { }, null, true, Optional.empty(), null);

        assertNotSame(previous, broker.exceptionListener);
        registry.shutdown();

        assertSame(previous, broker.exceptionListener);
    }

    @Test
    void aFailedAttemptIsRetriedAfterADelay() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSListenerRegistry registry = registry(recovery(true, 5, Duration.ofMillis(1)));
        registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE,
            message -> { }, null, true, Optional.empty(), null);
        broker.sessions.get(0).broken = true;
        broker.failedSessions.set(2);

        broker.reportError(SESSION_DIED);

        await(() -> broker.sessions.size() == 2 && broker.sessions.get(1).consumers.size() == 1);
        assertEquals(4, broker.sessionAttempts.get(), "the registration, two failed attempts and the one that worked");
        assertEquals(1, broker.sessions.get(1).consumers.size());
        registry.shutdown();
    }

    @Test
    void aConsumerKeepsRetryingAfterTheWarnAttemptsWithoutAnotherError() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSListenerRegistry registry = registry(recovery(true, 2, Duration.ofMillis(1)));
        registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE,
            message -> { }, null, true, Optional.empty(), null);
        broker.sessions.get(0).broken = true;
        // like Oracle AQ, whose ping no longer reports anything once the failed session is closed
        broker.failedSessions.set(5);

        broker.reportError(SESSION_DIED);

        await(() -> broker.sessions.size() == 2 && broker.sessions.get(1).consumers.size() == 1);
        assertEquals(7, broker.sessionAttempts.get(), "the registration, five failed attempts and the one that worked");
        registry.shutdown();
    }

    @Test
    void theRetriesStopWhenTheRegistryShutsDown() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSListenerRegistry registry = registry(recovery(true, 1, Duration.ofMillis(1)));
        registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE,
            message -> { }, null, true, Optional.empty(), null);
        broker.sessions.get(0).broken = true;
        broker.failedSessions.set(Integer.MAX_VALUE);
        broker.reportError(SESSION_DIED);
        await(() -> broker.sessionAttempts.get() >= 3);

        registry.shutdown();
        int attempts = broker.sessionAttempts.get();
        sleep(Duration.ofMillis(50));

        assertTrue(broker.sessionAttempts.get() <= attempts + 1, "at most the attempt in progress completes");
    }

    @Test
    void aListenerWhoseLastAttemptFailedReportsItsSessionAsBroken() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSListenerRegistry registry = registry(recovery(true, 1, Duration.ofHours(1)));
        JMSListener listener = registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false,
            Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.empty(), null);
        StubSession failed = broker.sessions.get(0);
        failed.broken = true;
        broker.failedSessions.set(1);

        registry.recover(listener, SESSION_DIED, 1);

        assertEquals(1, broker.sessions.size(), "no session was created");
        assertTrue(failed.closed);
        assertTrue(listener.isSessionBroken(), "a listener without a consumer still needs its session recreated");
        registry.shutdown();
    }

    @Test
    void aSessionWhoseConsumerCannotBeCreatedIsClosedAndRetried() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSListenerRegistry registry = registry(recovery(true, 1, Duration.ofMillis(1)));
        JMSListener listener = registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false,
            Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.empty(), null);
        broker.sessions.get(0).broken = true;
        broker.failedConsumers.set(1);

        registry.recover(listener, SESSION_DIED, 1);

        await(() -> broker.sessions.size() == 3 && broker.sessions.get(2).consumers.size() == 1);
        assertTrue(broker.sessions.get(1).closed, "the session whose consumer failed is closed");
        registry.shutdown();
    }

    @Test
    void aDurableSubscriptionResumesOnTheSameSubscriptionAndConnection() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSConnectionPool pool = new JMSConnectionPool(broker.connectionFactory, 0, 1);
        JMSListenerRegistry registry = registry(recovery(true, 3, Duration.ofMillis(1)));
        JMSListener listener = registry.register(pool, JMSDestinationType.TOPIC, "prices", false, Session.AUTO_ACKNOWLEDGE,
            message -> { }, null, true, Optional.empty(), new JMSTopicSubscription("price-feed", true, false, null));
        broker.sessions.get(0).broken = true;

        registry.recover(listener, SESSION_DIED, 1);

        assertEquals(List.of("durable:price-feed", "durable:price-feed"), broker.subscriptions);
        assertEquals(1, broker.connections.size(), "the dedicated connection keeps its client ID");
        assertEquals(List.of("price-feed"), broker.clientIds);
        registry.shutdown();
    }

    @Test
    void aSharedSubscriptionResumesOnTheSameSubscription() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSConnectionPool pool = new JMSConnectionPool(broker.connectionFactory, 0, 1);
        JMSListenerRegistry registry = registry(recovery(true, 3, Duration.ofMillis(1)));
        List<JMSListener> listeners = registry.register(pool, JMSDestinationType.TOPIC, "prices", false,
            Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.of("region = 'EU'"),
            new JMSTopicSubscription("price-feed", true, true, null), 2);
        broker.sessions.get(1).broken = true;

        registry.recover(listeners.get(0), SESSION_DIED, 1);
        registry.recover(listeners.get(1), SESSION_DIED, 1);

        assertEquals(List.of("shared-durable:price-feed:region = 'EU'", "shared-durable:price-feed:region = 'EU'",
            "shared-durable:price-feed:region = 'EU'"), broker.subscriptions);
        assertFalse(broker.sessions.get(0).closed);
        assertTrue(broker.sessions.get(1).closed);
        registry.shutdown();
    }

    @Test
    void aStoppedListenerIsNotRecreated() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSListenerRegistry registry = registry(recovery(true, 3, Duration.ofMillis(1)));
        JMSListener listener = registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false,
            Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.empty(), null);
        broker.sessions.get(0).broken = true;
        registry.shutdown();

        registry.recover(listener, SESSION_DIED, 1);

        assertNull(broker.exceptionListener);
        assertFalse(listener.isSessionBroken());
        assertFalse(listener.recreateSession());
        assertEquals(1, broker.sessions.size());
    }

    @Test
    void aListenerThatIsNotStartedDoesNotRecover() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSListenerRegistry registry = registry(recovery(true, 3, Duration.ofMillis(1)));
        JMSListener listener = registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false,
            Session.AUTO_ACKNOWLEDGE, message -> { }, null, false, Optional.empty(), null);
        broker.sessions.get(0).broken = true;

        assertFalse(listener.isSessionBroken());
        registry.shutdown();
    }

    @Test
    void aListenerCreatedWithoutTheRegistryCannotRecreateItsSession() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSListener listener = new JMSListener(broker.connection.createSession(false, Session.AUTO_ACKNOWLEDGE),
            message -> { }, JMSDestinationType.QUEUE, "orders", null, Optional.empty());
        listener.start();
        broker.sessions.get(0).broken = true;

        assertFalse(listener.isSessionBroken());
        assertFalse(listener.recreateSession());
        broker.sessions.get(0).broken = false;
        listener.stop();
    }

    @Test
    void recoveryCanBeDisabled() throws JMSException {
        StubBroker broker = new StubBroker();
        JMSListenerRegistry registry = registry(recovery(false, 3, Duration.ofMillis(1)));
        JMSListener listener = registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false,
            Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.empty(), null);
        broker.sessions.get(0).broken = true;

        assertNull(broker.exceptionListener, "the registry does not listen for errors");
        assertFalse(listener.isSessionBroken());
        registry.shutdown();
    }

    @Test
    void aProviderThatDoesNotReportErrorsStillRegistersTheListener() throws JMSException {
        StubBroker broker = new StubBroker();
        broker.exceptionListenerSupported = false;
        JMSListenerRegistry registry = registry(recovery(true, 3, Duration.ofMillis(1)));

        registry.register(broker.connection, JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE,
            message -> { }, null, true, Optional.empty(), null);

        assertEquals(1, broker.sessions.get(0).consumers.size());
        registry.shutdown();
    }

    @Test
    void theDelayDoublesAfterEachAttemptUpToTheMaximum() {
        JMSListenerRegistry registry = registry(recovery(true, 10, Duration.ofSeconds(1)));

        assertEquals(Duration.ofSeconds(1), registry.delay(1));
        assertEquals(Duration.ofSeconds(2), registry.delay(2));
        assertEquals(Duration.ofSeconds(4), registry.delay(3));
        assertEquals(Duration.ofSeconds(5), registry.delay(4));
        assertEquals(Duration.ofSeconds(5), registry.delay(30));
    }

    @Test
    void theRecoveryIsConfigurable() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.jms.listener-recovery.warn-attempts", "4",
            "micronaut.jms.listener-recovery.initial-delay", "250ms"))) {
            JMSListenerRecoveryConfiguration configuration = context.getBean(JMSListenerRecoveryConfiguration.class);

            assertTrue(configuration.isEnabled());
            assertEquals(4, configuration.getWarnAttempts());
            assertEquals(Duration.ofMillis(250), configuration.getInitialDelay());
            assertEquals(Duration.ofMinutes(1), configuration.getMaxDelay());
            assertEquals(Duration.ofMillis(500), context.getBean(JMSListenerRegistry.class).delay(2));
        }
    }

    @Test
    void theDefaultRecoveryConfigurationMatchesTheConfigurationDefaults() {
        JMSListenerRegistry registry = registry(JMSListenerRegistry.DEFAULT_RECOVERY);

        assertTrue(JMSListenerRegistry.DEFAULT_RECOVERY.isEnabled());
        assertEquals(10, JMSListenerRegistry.DEFAULT_RECOVERY.getWarnAttempts());

        assertEquals(Duration.ofSeconds(1), registry.delay(1));
        assertEquals(Duration.ofMinutes(1), registry.delay(10));
    }

    private static JMSListenerRegistry registry(JMSListenerRecoveryConfiguration configuration) {
        return new JMSListenerRegistry(List.of(), List.of(), List.of(), configuration);
    }

    private static JMSListenerRecoveryConfiguration recovery(boolean enabled, int warnAttempts, Duration initialDelay) {
        return new JMSListenerRecoveryConfiguration() {
            @Override
            public boolean isEnabled() {
                return enabled;
            }

            @Override
            public int getWarnAttempts() {
                return warnAttempts;
            }

            @Override
            public Duration getInitialDelay() {
                return initialDelay;
            }

            @Override
            public Duration getMaxDelay() {
                return initialDelay.multipliedBy(5);
            }
        };
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out");
            }
            sleep(Duration.ofMillis(5));
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static Message message() {
        return stub(Message.class, (name, args) -> null);
    }

    /**
     * A broker of stubs whose sessions fail their destination lookups once they are broken, like an Oracle AQ session
     * whose database session was disconnected.
     */
    static class StubBroker {

        final List<Connection> connections = new CopyOnWriteArrayList<>();
        final List<StubSession> sessions = new CopyOnWriteArrayList<>();
        final List<String> subscriptions = new CopyOnWriteArrayList<>();
        final List<String> clientIds = new CopyOnWriteArrayList<>();
        final AtomicInteger sessionAttempts = new AtomicInteger();
        final AtomicInteger failedSessions = new AtomicInteger();
        final AtomicInteger failedConsumers = new AtomicInteger();
        final Connection connection;
        final ConnectionFactory connectionFactory;
        volatile ExceptionListener exceptionListener;
        volatile boolean exceptionListenerSupported = true;

        StubBroker() {
            connection = stub(Connection.class, (name, args) -> switch (name) {
                case "createSession" -> createSession();
                case "getExceptionListener" -> exceptionListener();
                case "setExceptionListener" -> {
                    exceptionListener();
                    exceptionListener = (ExceptionListener) args[0];
                    yield null;
                }
                case "setClientID" -> {
                    clientIds.add((String) args[0]);
                    yield null;
                }
                default -> null;
            });
            connectionFactory = stub(ConnectionFactory.class, (name, args) -> {
                if ("createConnection".equals(name)) {
                    connections.add(connection);
                    return connection;
                }
                return null;
            });
        }

        private ExceptionListener exceptionListener() throws JMSException {
            if (!exceptionListenerSupported) {
                throw new JMSException("not supported");
            }
            return exceptionListener;
        }

        void reportError(JMSException error) {
            exceptionListener.onException(error);
        }

        private Session createSession() throws JMSException {
            sessionAttempts.incrementAndGet();
            if (failedSessions.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new JMSException("ORA-03113: end-of-file on communication channel");
            }
            StubSession session = new StubSession(this);
            sessions.add(session);
            return session.session;
        }
    }

    static class StubSession {

        final List<String> queues = new CopyOnWriteArrayList<>();
        final List<StubConsumer> consumers = new CopyOnWriteArrayList<>();
        final Session session;
        volatile boolean broken;
        volatile boolean closed;

        StubSession(StubBroker broker) {
            session = stub(Session.class, (name, args) -> switch (name) {
                case "createQueue" -> {
                    lookup();
                    queues.add((String) args[0]);
                    yield stub(jakarta.jms.Queue.class, (n, a) -> null);
                }
                case "createTopic" -> {
                    lookup();
                    yield stub(jakarta.jms.Topic.class, (n, a) -> null);
                }
                case "createConsumer" -> consumer(broker);
                case "createDurableSubscriber" -> {
                    broker.subscriptions.add("durable:" + args[1]);
                    yield consumer(broker);
                }
                case "createSharedDurableConsumer" -> {
                    broker.subscriptions.add("shared-durable:" + args[1] + ":" + args[2]);
                    yield consumer(broker);
                }
                case "close" -> {
                    closed = true;
                    if (broken) {
                        throw new JMSException("JMS-112: Connection is invalid");
                    }
                    yield null;
                }
                default -> null;
            });
        }

        private void lookup() throws JMSException {
            if (broken || closed) {
                throw new JMSException("ORA-03113: end-of-file on communication channel");
            }
        }

        private MessageConsumer consumer(StubBroker broker) throws JMSException {
            if (broker.failedConsumers.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new JMSException("consumer failed");
            }
            StubConsumer consumer = new StubConsumer();
            consumers.add(consumer);
            return consumer.consumer;
        }
    }

    static class StubConsumer {

        final MessageConsumer consumer;
        volatile MessageListener listener;
        volatile boolean closed;

        StubConsumer() {
            // a topic subscriber, which a durable subscription returns, is also a plain consumer
            consumer = stub(TopicSubscriber.class, (name, args) -> {
                switch (name) {
                    case "setMessageListener" -> listener = (MessageListener) args[0];
                    case "close" -> closed = true;
                    default -> {
                        // nothing to stub
                    }
                }
                return null;
            });
        }

        void deliver(Message message) {
            listener.onMessage(message);
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
