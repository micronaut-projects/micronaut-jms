package io.micronaut.jms.pool;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerSessionPoolTest {

    final AtomicInteger connections = new AtomicInteger();
    final AtomicInteger closedConnections = new AtomicInteger();
    final List<Session> created = new ArrayList<>();
    final List<Session> closed = new ArrayList<>();
    final AtomicBoolean failNextSession = new AtomicBoolean();

    @Test
    void sessionsAreReusedAcrossSends() throws JMSException {
        try (ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8)) {
            Session first = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            pool.release(first, false);
            Session second = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            pool.release(second, false);

            assertSame(first, second);
            assertEquals(1, created.size());
            assertEquals(1, connections.get(), "one connection for every session");
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
            assertEquals(1, connections.get());
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
            failNextSession.set(true);

            Session session = pool.borrow(false, Session.AUTO_ACKNOWLEDGE);
            pool.release(session, false);

            assertEquals(2, connections.get());
            assertEquals(1, closedConnections.get());
        }
    }

    @Test
    void closingThePoolClosesTheConnection() throws JMSException {
        ProducerSessionPool pool = new ProducerSessionPool(new JMSConnectionPool(factory(), 0, 1), 8);
        pool.release(pool.borrow(false, Session.AUTO_ACKNOWLEDGE), false);

        pool.close();

        assertEquals(1, closedConnections.get());
        assertEquals(created, closed);
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
            connections.incrementAndGet();
            return stub(Connection.class, (connectionMethod, connectionArgs) -> switch (connectionMethod) {
                case "createSession" -> {
                    if (failNextSession.getAndSet(false)) {
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
                    yield session[0];
                }
                case "close" -> {
                    closedConnections.incrementAndGet();
                    yield null;
                }
                default -> null;
            });
        });
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

    @FunctionalInterface
    private interface Answer {
        Object answer(String method, Object[] args) throws JMSException;
    }
}
