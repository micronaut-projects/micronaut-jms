package io.micronaut.jms.pool;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JMSConnectionPoolTest {

    final List<FakeConnection> connections = new ArrayList<>();

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

    private ConnectionFactory factory() {
        return (ConnectionFactory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ConnectionFactory.class},
            (proxy, method, args) -> {
                if ("createConnection".equals(method.getName())) {
                    FakeConnection connection = new FakeConnection();
                    connections.add(connection);
                    return connection.proxy;
                }
                return null;
            });
    }

    static final class FakeConnection {

        ExceptionListener listener;
        boolean closed;
        final Connection proxy = (Connection) Proxy.newProxyInstance(FakeConnection.class.getClassLoader(), new Class<?>[]{Connection.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setExceptionListener" -> {
                    listener = (ExceptionListener) args[0];
                    yield null;
                }
                case "close" -> {
                    closed = true;
                    yield null;
                }
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> null;
            });

        void fail() {
            listener.onException(new JMSException("connection lost"));
        }
    }
}
