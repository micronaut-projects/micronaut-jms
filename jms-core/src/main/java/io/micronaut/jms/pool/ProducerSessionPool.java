/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jms.pool;

import jakarta.jms.Connection;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import org.apache.commons.pool2.KeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reuses the sessions of producers across sends, instead of opening a connection and a session for each message.
 * The sessions are created on one connection from the connection factory of a {@link JMSConnectionPool}, which this
 * pool opens on the first send and closes with itself. A session is used by one send at a time, and a session that
 * failed is closed rather than reused. At most {@code maxActive} sessions are open: when all of them are borrowed, a
 * send waits up to {@code maxWait} for one to be released.
 *
 * <p>When a session cannot be created, or the provider reports a failure of the connection to its
 * {@link ExceptionListener}, the connection is replaced. Its idle sessions are closed, its borrowed sessions are
 * closed when they are released, and the connection is closed once the last of its sessions is.</p>
 *
 * @since 5.2.0
 */
public final class ProducerSessionPool implements AutoCloseable {

    /**
     * The default maximum number of open sessions.
     */
    public static final int DEFAULT_MAX_ACTIVE = 32;

    /**
     * The default time that a send waits for a session when the maximum number of sessions are borrowed.
     */
    public static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(10);

    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerSessionPool.class);

    private final SessionFactory factory;
    private final GenericKeyedObjectPool<SessionMode, Session> sessions;
    private final Map<Session, SessionMode> borrowed = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Duration maxWait;
    // set when a connection is replaced, so that its idle sessions are closed
    private final AtomicBoolean clearIdle = new AtomicBoolean();

    /**
     * @param connectionPool The pool of the connection that the sessions are created on
     * @param maxIdle        The maximum number of idle sessions kept for each session mode
     */
    public ProducerSessionPool(JMSConnectionPool connectionPool, int maxIdle) {
        this(connectionPool, maxIdle, DEFAULT_MAX_ACTIVE, DEFAULT_MAX_WAIT);
    }

    /**
     * @param connectionPool The pool of the connection that the sessions are created on
     * @param maxIdle        The maximum number of idle sessions kept for each session mode
     * @param maxActive      The maximum number of open sessions, borrowed or idle, of all session modes
     * @param maxWait        How long a send waits for a session when {@code maxActive} sessions are borrowed
     */
    public ProducerSessionPool(JMSConnectionPool connectionPool, int maxIdle, int maxActive, Duration maxWait) {
        this.factory = new SessionFactory(connectionPool);
        this.maxWait = maxWait;
        GenericKeyedObjectPoolConfig<Session> config = new GenericKeyedObjectPoolConfig<>();
        // when maxActive sessions are borrowed, a send waits for one instead of opening another
        config.setMaxTotalPerKey(maxActive);
        config.setMaxTotal(maxActive);
        config.setMaxIdlePerKey(maxIdle);
        config.setBlockWhenExhausted(true);
        config.setMaxWait(maxWait);
        // the sessions of a replaced connection are closed rather than handed out or kept
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setJmxEnabled(false);
        this.sessions = new GenericKeyedObjectPool<>(factory, config);
    }

    /**
     * Borrows a session, which must be returned with {@link #release(Session, boolean)}. When the maximum number of
     * sessions are borrowed, waits for one to be released.
     *
     * @param transacted      Whether the session is transacted
     * @param acknowledgeMode The acknowledge mode of the session
     * @return The session
     * @throws JMSException If the session cannot be created, or no session was released in time
     */
    public Session borrow(boolean transacted, int acknowledgeMode) throws JMSException {
        SessionMode mode = new SessionMode(transacted, acknowledgeMode);
        long start = System.nanoTime();
        try {
            Session session = sessions.borrowObject(mode);
            borrowed.put(session, mode);
            return session;
        } catch (NoSuchElementException e) {
            String message = System.nanoTime() - start >= maxWait.toNanos()
                ? "No producer session was released within " + maxWait + ", all " + sessions.getMaxTotal() + " sessions are in use"
                : "Unable to create a producer session: " + e.getMessage();
            JMSException failure = new JMSException(message);
            failure.initCause(e);
            throw failure;
        } catch (JMSException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            JMSException failure = new JMSException("Unable to create a producer session: " + e.getMessage());
            failure.initCause(e);
            throw failure;
        } finally {
            clearIdleSessions();
        }
    }

    /**
     * Returns a borrowed session.
     *
     * @param session The session
     * @param broken  Whether a send failed on the session, so it is closed instead of reused
     */
    public void release(Session session, boolean broken) {
        SessionMode mode = borrowed.remove(session);
        if (mode == null) {
            throw new IllegalArgumentException("The session was not borrowed from this pool: " + session);
        }
        try {
            if (broken) {
                sessions.invalidateObject(mode, session);
            } else {
                sessions.returnObject(mode, session);
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to return a producer session: {}", e.getMessage(), e);
        }
    }

    /**
     * @return The number of sessions that are currently idle
     */
    public int getIdleCount() {
        return sessions.getNumIdle();
    }

    /**
     * @return The number of sessions that are currently borrowed
     */
    public int getBorrowedCount() {
        return sessions.getNumActive();
    }

    /**
     * Closes the idle sessions and the connections, which also closes the sessions that are still borrowed.
     */
    @Override
    public void close() {
        sessions.close();
        factory.closeConnections();
    }

    private void clearIdleSessions() {
        if (clearIdle.compareAndSet(true, false)) {
            sessions.clear();
        }
    }

    private record SessionMode(boolean transacted, int acknowledgeMode) {
    }

    /**
     * A connection that sessions are created on, with the number of its sessions that are open.
     */
    private static final class PoolConnection {

        private final Connection connection;
        private int openSessions;
        private volatile boolean replaced;
        private boolean closed;

        private PoolConnection(Connection connection) {
            this.connection = connection;
        }
    }

    /**
     * A pooled session with the connection it was created on.
     */
    private static final class PooledSession extends DefaultPooledObject<Session> {

        private final PoolConnection connection;

        private PooledSession(Session session, PoolConnection connection) {
            super(session);
            this.connection = connection;
        }
    }

    /**
     * Creates the sessions on the current connection, which it opens and replaces.
     */
    private final class SessionFactory implements KeyedPooledObjectFactory<SessionMode, Session> {

        private final JMSConnectionPool connectionPool;
        private final List<PoolConnection> connections = new ArrayList<>();
        private @Nullable PoolConnection current;
        private boolean closed;

        private SessionFactory(JMSConnectionPool connectionPool) {
            this.connectionPool = connectionPool;
        }

        @Override
        public PooledObject<Session> makeObject(SessionMode mode) throws JMSException {
            PoolConnection connection = openSession();
            try {
                return new PooledSession(connection.connection.createSession(mode.transacted(), mode.acknowledgeMode()), connection);
            } catch (JMSException | RuntimeException e) {
                // the connection may have failed: the next attempt uses a new one
                LOGGER.debug("Unable to create a producer session, retrying on a new connection: {}", e.getMessage());
                sessionClosed(connection);
                replace(connection);
                PoolConnection retry = openSession();
                try {
                    return new PooledSession(retry.connection.createSession(mode.transacted(), mode.acknowledgeMode()), retry);
                } catch (JMSException | RuntimeException retryFailure) {
                    sessionClosed(retry);
                    retryFailure.addSuppressed(e);
                    throw retryFailure;
                }
            }
        }

        @Override
        public boolean validateObject(SessionMode mode, PooledObject<Session> pooled) {
            return !((PooledSession) pooled).connection.replaced;
        }

        @Override
        public void destroyObject(SessionMode mode, PooledObject<Session> pooled) {
            try {
                pooled.getObject().close();
            } catch (JMSException | RuntimeException e) {
                LOGGER.debug("Unable to close a producer session: {}", e.getMessage(), e);
            } finally {
                sessionClosed(((PooledSession) pooled).connection);
            }
        }

        @Override
        public void activateObject(SessionMode mode, PooledObject<Session> pooled) {
            // sessions need no activation
        }

        @Override
        public void passivateObject(SessionMode mode, PooledObject<Session> pooled) {
            // sessions need no passivation
        }

        /**
         * @return The current connection, opened if there is none, counting the session about to be created on it
         */
        private synchronized PoolConnection openSession() throws JMSException {
            if (closed) {
                throw new JMSException("The producer session pool is closed");
            }
            PoolConnection connection = current;
            if (connection == null) {
                connection = new PoolConnection(connectionPool.getConnectionFactory().createConnection());
                listen(connection);
                connections.add(connection);
                current = connection;
            }
            connection.openSessions++;
            return connection;
        }

        private void listen(PoolConnection connection) {
            try {
                // calls a listener that the connection factory set as well
                ExceptionListener previous = connection.connection.getExceptionListener();
                connection.connection.setExceptionListener(e -> {
                    LOGGER.debug("The producer connection failed, replacing it: {}", e.getMessage());
                    replace(connection);
                    clearIdleSessions();
                    if (previous != null) {
                        previous.onException(e);
                    }
                });
            } catch (JMSException | RuntimeException e) {
                // for example in a Jakarta EE container, where applications cannot set exception listeners
                LOGGER.debug("Unable to listen for failures of the producer connection: {}", e.getMessage(), e);
            }
        }

        /**
         * Replaces the connection if it is still the current one, and closes it once none of its sessions are open.
         */
        private void replace(PoolConnection connection) {
            synchronized (this) {
                if (current != connection) {
                    // already replaced, after a concurrent failure
                    return;
                }
                current = null;
                connection.replaced = true;
                clearIdle.set(true);
                if (connection.openSessions > 0) {
                    return;
                }
            }
            close(connection);
        }

        private void sessionClosed(PoolConnection connection) {
            synchronized (this) {
                connection.openSessions--;
                if (!connection.replaced || connection.openSessions > 0) {
                    return;
                }
            }
            close(connection);
        }

        private void closeConnections() {
            List<PoolConnection> open;
            synchronized (this) {
                closed = true;
                current = null;
                open = new ArrayList<>(connections);
                open.forEach(connection -> connection.replaced = true);
            }
            open.forEach(this::close);
        }

        private void close(PoolConnection connection) {
            synchronized (this) {
                if (connection.closed) {
                    return;
                }
                connection.closed = true;
                connections.remove(connection);
            }
            try {
                connection.connection.close();
            } catch (JMSException | RuntimeException e) {
                LOGGER.debug("Unable to close the producer connection: {}", e.getMessage(), e);
            }
        }
    }
}
