package io.micronaut.jms.pool;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerSessionPoolTest {

    final List<StubConnection> connections = new CopyOnWriteArrayList<>();
    final List<Session> created = new CopyOnWriteArrayList<>();
    final List<Session> closed = new CopyOnWriteArrayList<>();
    final Map<Session, StubConnection> sessionConnections = new ConcurrentHashMap<>();
    // the number of the next createSession calls that fail
    final AtomicInteger failingSessions = new AtomicInteger();
    volatile boolean firstConnectionFails;
    volatile CyclicBarrier sessionBarrier;
    volatile ExceptionListener factoryListener;
    volatile boolean listenersUnsupported;

    @Test
    void sessionsAreReusedAcrossSends() throws JMSException {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            Session first = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            assertEquals(1, pool.getBorrowedCount());
            pool.release(first, false);
            Session second = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            pool.release(second, false);

            assertSame(first, second);
            assertEquals(1, created.size());
            assertEquals(1, connections.size(), "one connection for every session");
            assertEquals(0, pool.getBorrowedCount());
        }
    }

    @Test
    void concurrentSendsGetTheirOwnSessionsAndTransactedOnesAreSeparate() throws JMSException {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            Session first = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            Session second = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            Session transacted = pool.borrow(true, Session.SESSION_TRANSACTED);
            pool.release(first, false);
            pool.release(second, false);
            pool.release(transacted, false);

            assertNotSame(first, second);
            assertEquals(3, created.size());
            assertSame(transacted, pool.borrow(true, Session.SESSION_TRANSACTED));
            assertEquals(1, connections.size());
        }
    }

    @Test
    void aBrokenSessionIsClosedAndReplaced() throws JMSException {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            Session broken = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            pool.release(broken, true);
            Session next = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);

            assertTrue(closed.contains(broken));
            assertNotSame(broken, next);
        }
    }

    @Test
    void idleSessionsAreLimited() throws JMSException {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 1)) {
            Session first = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            Session second = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            pool.release(first, false);
            pool.release(second, false);

            assertEquals(1, pool.getIdleCount());
            assertEquals(1, closed.size());
        }
    }

    @Test
    void aFailedSessionCreationReplacesTheConnection() throws JMSException {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            pool.release(pool.borrow(false, Session.AUTO_ACKNOWLEDGE), true);
            failingSessions.set(1);

            Session session = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            pool.release(session, false);

            assertEquals(2, connections.size());
            assertTrue(connections.get(0).closed);
            assertFalse(connections.get(1).closed);
        }
    }

    @Test
    void aSessionThatCannotBeCreatedOnANewConnectionEitherFailsTheSend() {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            failingSessions.set(2);

            JMSException failure = assertThrows(JMSException.class, () -> pool.borrow(false, Session.AUTO_ACKNOWLEDGE));

            assertEquals(1, failure.getSuppressed().length);
            assertEquals(2, connections.size());
            assertTrue(connections.get(0).closed, "the replaced connection has no sessions");
        }
    }

    @Test
    void concurrentFailuresReplaceTheConnectionOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            // both sends fail on the first connection, at the same time
            firstConnectionFails = true;
            sessionBarrier = new CyclicBarrier(2);
            Future<Session> first = executor.submit(() -> pool.borrow(false, Session.AUTO_ACKNOWLEDGE));
            Future<Session> second = executor.submit(() -> pool.borrow(false, Session.AUTO_ACKNOWLEDGE));
            Session firstSession = first.get(5, TimeUnit.SECONDS);
            Session secondSession = second.get(5, TimeUnit.SECONDS);

            assertEquals(2, connections.size(), "the second failure does not replace the new connection");
            StubConnection replacement = connections.get(1);
            assertSame(replacement, sessionConnections.get(firstSession));
            assertSame(replacement, sessionConnections.get(secondSession));
            assertFalse(replacement.closed);
            assertTrue(connections.get(0).closed);
            assertFalse(closed.contains(firstSession));
            assertFalse(closed.contains(secondSession));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void idleSessionsOfAReplacedConnectionAreNotHandedOut() throws JMSException {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            Session transacted = pool.borrow(true, Session.SESSION_TRANSACTED);
            pool.release(transacted, false);
            failingSessions.set(1);
            pool.release(pool.borrow(false, Session.AUTO_ACKNOWLEDGE), false);

            Session next = pool.borrow(true, Session.SESSION_TRANSACTED);

            assertNotSame(transacted, next);
            assertTrue(closed.contains(transacted));
            assertTrue(connections.get(0).closed, "closed with its last session");
            assertSame(connections.get(1), sessionConnections.get(next));
        }
    }

    @Test
    void aSessionBorrowedWhenItsConnectionIsReplacedIsClosedWhenReleased() throws JMSException {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            Session inFlight = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            failingSessions.set(1);
            Session other = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);

            assertFalse(connections.get(0).closed, "a send in progress keeps its connection");
            assertFalse(closed.contains(inFlight));

            pool.release(inFlight, false);
            pool.release(other, false);

            assertTrue(closed.contains(inFlight));
            assertTrue(connections.get(0).closed);
            Session first = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            Session second = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            assertNotSame(inFlight, first);
            assertNotSame(inFlight, second);
            assertSame(other, first);
        }
    }

    @Test
    void aConnectionFailureReportedByTheProviderReplacesTheConnection() throws JMSException {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            Session idle = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            Session inFlight = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            pool.release(idle, false);

            connections.get(0).listener.onException(new JMSException("connection lost"));

            assertTrue(closed.contains(idle));
            assertEquals(0, pool.getIdleCount());
            assertFalse(connections.get(0).closed);
            pool.release(inFlight, false);
            assertTrue(connections.get(0).closed);
            Session next = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            assertSame(connections.get(1), sessionConnections.get(next));
        }
    }

    @Test
    void theExceptionListenerOfTheConnectionFactoryIsStillCalled() throws JMSException {
        List<JMSException> reported = new CopyOnWriteArrayList<>();
        factoryListener = reported::add;
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            pool.release(pool.borrow(false, Session.AUTO_ACKNOWLEDGE), false);
            JMSException failure = new JMSException("connection lost");

            connections.get(0).listener.onException(failure);

            assertEquals(List.of(failure), reported);
            assertTrue(connections.get(0).closed);
        }
    }

    @Test
    void sessionsArePooledWhenTheProviderDoesNotAllowExceptionListeners() throws JMSException {
        listenersUnsupported = true;
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            Session first = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            pool.release(first, false);

            assertSame(first, pool.borrow(false, Session.AUTO_ACKNOWLEDGE));
        }
    }

    @Test
    void aSendWaitsForASessionWhenTheMaximumAreBorrowed() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8, 1, Duration.ofSeconds(10))) {
            Session first = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            Future<Session> waiting = executor.submit(() -> pool.borrow(false, Session.AUTO_ACKNOWLEDGE));
            assertThrows(TimeoutException.class, () -> waiting.get(200, TimeUnit.MILLISECONDS));

            pool.release(first, false);

            assertSame(first, waiting.get(5, TimeUnit.SECONDS));
            assertEquals(1, created.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void theMaximumIsSharedBySessionModes() throws JMSException {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8, 1, Duration.ofSeconds(10))) {
            Session plain = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            pool.release(plain, false);

            Session transacted = pool.borrow(true, Session.SESSION_TRANSACTED);

            assertTrue(closed.contains(plain), "the idle session makes room for the other mode");
            assertNotSame(plain, transacted);
        }
    }

    @Test
    void aSendFailsWhenNoSessionIsReleasedInTime() throws JMSException {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8, 1, Duration.ofMillis(50))) {
            pool.borrow(false, Session.AUTO_ACKNOWLEDGE);

            JMSException timeout = assertThrows(JMSException.class, () -> pool.borrow(false, Session.AUTO_ACKNOWLEDGE));

            assertTrue(timeout.getMessage().contains("No producer session was released within PT0.05S, all 1 sessions are in use"),
                timeout.getMessage());
            assertEquals(1, created.size());
        }
    }

    @Test
    void closingThePoolClosesTheConnection() throws JMSException {
        ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8);
        pool.release(pool.borrow(false, Session.AUTO_ACKNOWLEDGE), false);
        Session borrowed = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
        Session other = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);

        pool.close();

        assertTrue(connections.get(0).closed);
        assertEquals(1, connections.get(0).closeCalls.get());
        pool.release(borrowed, false);
        pool.release(other, true);
        assertEquals(1, connections.get(0).closeCalls.get(), "closed once");
        assertThrows(IllegalStateException.class, () -> pool.borrow(false, Session.AUTO_ACKNOWLEDGE));
    }

    @Test
    void onlyBorrowedSessionsCanBeReleased() {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            Session other = stub(Session.class, (name, args) -> null);
            assertThrows(IllegalArgumentException.class, () -> pool.release(other, false));
        }
    }

    private ConnectionFactory factory() {
        return stub(ConnectionFactory.class, (name, args) -> {
            if (!"createConnection".equals(name)) {
                return null;
            }
            StubConnection connection = new StubConnection();
            connection.listener = factoryListener;
            connections.add(connection);
            return stub(Connection.class, (connectionMethod, connectionArgs) -> switch (connectionMethod) {
                case "createSession" -> createSession(connection);
                case "getExceptionListener" -> connection.listener;
                case "setExceptionListener" -> {
                    if (listenersUnsupported) {
                        throw new IllegalStateException("not allowed");
                    }
                    connection.listener = (ExceptionListener) connectionArgs[0];
                    yield null;
                }
                case "close" -> {
                    connection.closed = true;
                    connection.closeCalls.incrementAndGet();
                    yield null;
                }
                default -> null;
            });
        });
    }

    private Session createSession(StubConnection connection) throws JMSException {
        if (connection.closed) {
            throw new IllegalStateException("The connection is closed");
        }
        CyclicBarrier barrier = sessionBarrier;
        if (barrier != null && connection == connections.get(0)) {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                throw new IllegalStateException(e);
            }
        }
        if ((firstConnectionFails && connection == connections.get(0))
            || failingSessions.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new JMSException("connection failed");
        }
        Session[] session = new Session[1];
        session[0] = stub(Session.class, (sessionMethod, sessionArgs) -> {
            if ("close".equals(sessionMethod)) {
                closed.add(session[0]);
            }
            return null;
        });
        created.add(session[0]);
        sessionConnections.put(session[0], connection);
        return session[0];
    }

    private static <T> T stub(Class<T> type, Answer answer) {
        Object stub = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> type.getSimpleName() + " stub";
            default -> answer.answer(method.getName(), args);
        });
        return type.cast(stub);
    }

    static final class StubConnection {
        volatile ExceptionListener listener;
        volatile boolean closed;
        final AtomicInteger closeCalls = new AtomicInteger();
    }

    @FunctionalInterface
    private interface Answer {
        Object answer(String method, Object[] args) throws JMSException;
    }
}
