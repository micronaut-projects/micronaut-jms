package io.micronaut.jms.listener;

import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JMSListenerRegistryTest {

    @Test
    void shutdownReturnsListenerConnectionsToThePool() {
        JMSConnectionPool pool = new JMSConnectionPool(connectionFactory(false), 0, 1);
        JMSListenerRegistry registry = new JMSListenerRegistry(Collections.emptyList(), Collections.emptyList());
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> register(registry, pool.createConnection()));
            registry.shutdown();
        }
    }

    @Test
    void sharedConnectionIsReturnedOnce() throws JMSException {
        JMSConnectionPool pool = new JMSConnectionPool(connectionFactory(false), 0, 1);
        JMSListenerRegistry registry = new JMSListenerRegistry(Collections.emptyList(), Collections.emptyList());
        Connection connection = pool.createConnection();
        register(registry, connection);
        register(registry, connection);
        registry.shutdown();

        assertDoesNotThrow(() -> {
            pool.createConnection();
        });
        assertThrows(IllegalStateException.class, pool::createConnection);
    }

    @Test
    void connectionOfListenerThatFailsToStopStaysOutOfThePool() throws JMSException {
        JMSConnectionPool pool = new JMSConnectionPool(connectionFactory(true), 0, 1);
        JMSListenerRegistry registry = new JMSListenerRegistry(Collections.emptyList(), Collections.emptyList());
        register(registry, pool.createConnection());
        registry.shutdown();

        assertThrows(IllegalStateException.class, pool::createConnection);
    }

    private static void register(JMSListenerRegistry registry, Connection connection) throws JMSException {
        registry.register(connection, JMSDestinationType.QUEUE, "queue", false, Session.AUTO_ACKNOWLEDGE,
            message -> { }, null, true, Optional.empty());
    }

    private static ConnectionFactory connectionFactory(boolean sessionFailsToClose) {
        MessageConsumer consumer = stub(MessageConsumer.class, null);
        Queue queue = stub(Queue.class, null);
        Session session = stub(Session.class, (name, args) -> switch (name) {
            case "createQueue" -> queue;
            case "createConsumer" -> consumer;
            case "close" -> {
                if (sessionFailsToClose) {
                    throw new JMSException("session failed to close");
                }
                yield null;
            }
            default -> null;
        });
        Connection connection = stub(Connection.class, (name, args) -> "createSession".equals(name) ? session : null);
        return stub(ConnectionFactory.class, (name, args) -> "createConnection".equals(name) ? connection : null);
    }

    private static <T> T stub(Class<T> type, Answer answer) {
        Object stub = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> type.getSimpleName() + " stub";
            default -> answer == null ? null : answer.answer(method.getName(), args);
        });
        return type.cast(stub);
    }

    @FunctionalInterface
    private interface Answer {
        Object answer(String method, Object[] args) throws JMSException;
    }
}
