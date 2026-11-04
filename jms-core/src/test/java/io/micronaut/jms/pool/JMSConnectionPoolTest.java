package io.micronaut.jms.pool;

import io.micronaut.messaging.exceptions.MessagingSystemException;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JMSConnectionPoolTest {

    final List<FakeConnection> connections = new ArrayList<>();
    boolean rejectExceptionListener;
    ExceptionListener providerListener;

    @Test
    void healthyConnectionsAreReused() throws JMSException {
        JMSConnectionPool pool = new JMSConnectionPool(factory(), 0, 1);
        Connection first = pool.createConnection();
        first.close();

        assertSame(first, pool.createConnection());
        assertEquals(1, connections.size());
    }

    @Test
    void aConnectionThatFailsWhileIdleIsReplaced() throws JMSException {
        JMSConnectionPool pool = new JMSConnectionPool(factory(), 0, 1);
        Connection first = pool.createConnection();
        first.close();
        connections.get(0).fail();

        Connection second = pool.createConnection();

        assertNotSame(first, second);
        assertTrue(connections.get(0).closed);
        assertEquals(2, connections.size());
    }

    @Test
    void aConnectionThatFailsWhileInUseIsClosedWhenReleased() throws JMSException {
        JMSConnectionPool pool = new JMSConnectionPool(factory(), 0, 1);
        Connection first = pool.createConnection();
        connections.get(0).fail();
        first.close();

        assertTrue(connections.get(0).closed);
        assertNotSame(first, pool.createConnection(), "the failed connection no longer counts against the maximum");
    }

    @Test
    void theExceptionListenerOfTheApplicationIsStillCalled() throws JMSException {
        JMSConnectionPool pool = new JMSConnectionPool(factory(), 0, 1);
        Connection connection = pool.createConnection();
        List<JMSException> reported = new ArrayList<>();
        ExceptionListener listener = reported::add;
        connection.setExceptionListener(listener);

        connections.get(0).fail();

        assertSame(listener, connection.getExceptionListener());
        assertEquals(1, reported.size());
        assertTrue(((PooledConnection) connection).isBroken());
    }

    @Test
    void theExceptionListenerThatTheProviderSetIsKeptAndCalled() throws JMSException {
        List<JMSException> reported = new ArrayList<>();
        providerListener = reported::add;
        JMSConnectionPool pool = new JMSConnectionPool(factory(), 0, 1);
        Connection connection = pool.createConnection();

        assertSame(providerListener, connection.getExceptionListener());

        connections.get(0).fail();

        assertEquals(1, reported.size());
        assertSame(reported.get(0), ((PooledConnection) connection).getFailure(), "the reported error is kept for the warning");
    }

    @Test
    void aConnectionWhoseProviderRejectsTheExceptionListenerIsStillPooled() throws JMSException {
        JMSConnectionPool pool = new JMSConnectionPool(factory(), 0, 1);
        rejectExceptionListener = true;
        Connection first = pool.createConnection();
        first.close();

        assertSame(first, pool.createConnection());
        assertFalse(((PooledConnection) first).isBroken());
    }

    @Test
    void aFailedConnectionThatCannotBeClosedIsStillDiscarded() throws JMSException {
        JMSConnectionPool pool = new JMSConnectionPool(factory(), 0, 1);
        Connection first = pool.createConnection();
        connections.get(0).failClose = true;
        connections.get(0).fail();

        assertDoesNotThrow(first::close);
        assertNotSame(first, pool.createConnection());
        assertEquals(2, connections.size());
    }

    @Test
    void aPoolThatDoesNotValidateItsObjectsReusesEveryReleasedObject() throws JMSException {
        AbstractPool<PooledObject<String>> pool = new AbstractPool<>(0, 1) {
            @Override
            protected PooledObject<String> create(Object... args) {
                return new PooledObject<>(this, "object") {
                };
            }

            @Override
            protected void reset(PooledObject<String> pooledObject) {
                // nothing to reset
            }
        };
        PooledObject<String> first = pool.request();
        first.close();

        assertSame(first, pool.request());
    }

    @Test
    void aPoolDiscardsTheObjectsThatItReportsAsInvalid() throws JMSException {
        List<PooledObject<String>> created = new ArrayList<>();
        AbstractPool<PooledObject<String>> pool = new AbstractPool<>(0, 1) {
            @Override
            protected PooledObject<String> create(Object... args) {
                PooledObject<String> object = new PooledObject<>(this, "object") {
                };
                created.add(object);
                return object;
            }

            @Override
            protected void reset(PooledObject<String> pooledObject) {
                // nothing to reset
            }

            @Override
            protected boolean isValid(PooledObject<String> pooledObject) {
                return false;
            }
        };
        PooledObject<String> first = pool.request();
        first.close();

        assertNotSame(first, pool.request(), "the released object left the pool");
        assertEquals(2, created.size());
    }

    @Test
    void aConnectionThatCannotBeCreatedFailsTheRequest() {
        ConnectionFactory failing = (ConnectionFactory) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{ConnectionFactory.class}, (proxy, method, args) -> {
                throw new JMSException("broker unavailable");
            });
        JMSConnectionPool pool = new JMSConnectionPool(failing, 0, 1);

        MessagingSystemException e = assertThrows(MessagingSystemException.class, pool::createConnection);

        assertEquals("broker unavailable", e.getCause().getMessage());
    }

    private ConnectionFactory factory() {
        return (ConnectionFactory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ConnectionFactory.class},
            (proxy, method, args) -> {
                if ("createConnection".equals(method.getName())) {
                    FakeConnection connection = new FakeConnection(rejectExceptionListener);
                    connection.listener = providerListener;
                    connections.add(connection);
                    return connection.proxy;
                }
                return null;
            });
    }

    static final class FakeConnection {

        boolean rejectExceptionListener;
        ExceptionListener listener;
        boolean closed;
        boolean failClose;
        final Connection proxy = (Connection) Proxy.newProxyInstance(FakeConnection.class.getClassLoader(), new Class<?>[]{Connection.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setExceptionListener" -> {
                    if (rejectExceptionListener) {
                        throw new JMSException("not supported");
                    }
                    listener = (ExceptionListener) args[0];
                    yield null;
                }
                case "getExceptionListener" -> listener;
                case "close" -> {
                    if (failClose) {
                        throw new JMSException("already closed");
                    }
                    closed = true;
                    yield null;
                }
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> null;
            });

        FakeConnection(boolean rejectExceptionListener) {
            this.rejectExceptionListener = rejectExceptionListener;
        }

        void fail() {
            listener.onException(new JMSException("connection lost"));
        }
    }
}
