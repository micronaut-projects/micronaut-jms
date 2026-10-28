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
import org.apache.commons.pool2.impl.EvictionConfig;
import org.apache.commons.pool2.impl.EvictionPolicy;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reuses the sessions of producers across sends, instead of opening a connection and a session for each message.
 * The sessions are created on one connection from the connection factory of a {@link JMSConnectionPool}, which this
 * pool opens on the first send and closes with itself. A session is used by one send at a time, and a session that
 * failed is closed rather than reused. At most {@code maxActive} sessions are borrowed at once, of all session modes:
 * when all of them are borrowed, a send waits up to {@code maxWait} for one to be released.
 *
 * <p>A session released while sends are waiting is kept for them, so a burst of sends reuses at most
 * {@code maxActive} sessions of each session mode instead of closing and opening sessions. Once no send waits, a
 * released session is closed if {@code maxIdle} sessions of its mode are idle already, and a periodic check closes
 * the oldest idle sessions above {@code maxIdle} that remain, for example sessions kept for a send that took one of
 * another mode.</p>
 *
 * <p>When a session cannot be created, or the provider reports a failure of the connection to its
 * {@link ExceptionListener}, the connection is replaced. Its idle sessions are closed, its borrowed sessions are
 * closed when they are released, and the connection is closed once the last of its sessions is.</p>
 *
 * @since 5.2.0
 */
public final class ProducerSessionPool implements AutoCloseable {

    /**
     * The default maximum number of sessions that are borrowed at once.
     */
    public static final int DEFAULT_MAX_ACTIVE = 32;

    /**
     * The default time that a send waits for a session when the maximum number of sessions are borrowed.
     */
    public static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(10);

    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerSessionPool.class);

    // how often idle sessions above maxIdle are closed, when no release did so
    private static final Duration TRIM_INTERVAL = Duration.ofSeconds(30);

    private final SessionFactory factory;
    private final GenericKeyedObjectPool<SessionMode, Session> sessions;
    private final Map<Session, SessionMode> borrowed = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Duration maxWait;
    private final int maxIdle;
    private final int maxActive;
    // a permit for each session that can be borrowed, shared by all session modes
    private final Semaphore permits;
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
     * @param maxIdle        The maximum number of idle sessions kept for each session mode once no send waits
     * @param maxActive      The maximum number of sessions borrowed at once, of all session modes
     * @param maxWait        How long a send waits for a session when {@code maxActive} sessions are borrowed
     */
    public ProducerSessionPool(JMSConnectionPool connectionPool, int maxIdle, int maxActive, Duration maxWait) {
        this(connectionPool, maxIdle, maxActive, maxWait, TRIM_INTERVAL);
    }

    /**
     * @param connectionPool The pool of the connection that the sessions are created on
     * @param maxIdle        The maximum number of idle sessions kept for each session mode once no send waits
     * @param maxActive      The maximum number of sessions borrowed at once, of all session modes
     * @param maxWait        How long a send waits for a session when {@code maxActive} sessions are borrowed
     * @param trimInterval   How often the idle sessions above {@code maxIdle} are closed
     */
    ProducerSessionPool(JMSConnectionPool connectionPool, int maxIdle, int maxActive, Duration maxWait, Duration trimInterval) {
        this.factory = new SessionFactory(connectionPool);
        this.maxWait = maxWait;
        this.maxIdle = maxIdle;
        this.maxActive = maxActive;
        this.permits = new Semaphore(maxActive, true);
        GenericKeyedObjectPoolConfig<Session> config = new GenericKeyedObjectPoolConfig<>();
        // the permits limit the borrowed sessions of all modes, so that pool2 never closes idle sessions to make room
        // for new ones, and released sessions are kept while sends wait for them
        config.setMaxTotalPerKey(maxActive);
        config.setMaxTotal(-1);
        config.setMaxIdlePerKey(maxActive);
        config.setBlockWhenExhausted(true);
        config.setMaxWait(maxWait);
        // closes the oldest idle sessions above maxIdle of each mode
        config.setEvictionPolicy(new TrimPolicy(maxIdle));
        config.setTimeBetweenEvictionRuns(trimInterval);
        config.setNumTestsPerEvictionRun(-1);
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
        if (sessions.isClosed()) {
            throw new IllegalStateException("The producer session pool is closed");
        }
        try {
            if (!permits.tryAcquire(maxWait.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new JMSException("No producer session was released within " + maxWait + ", all " + maxActive + " sessions are in use");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            JMSException failure = new JMSException("Interrupted while waiting for a producer session");
            failure.initCause(e);
            throw failure;
        }
        boolean done = false;
        try {
            Session session = sessions.borrowObject(mode);
            borrowed.put(session, mode);
            done = true;
            return session;
        } catch (NoSuchElementException e) {
            // for example a new session whose connection was replaced meanwhile
            JMSException failure = new JMSException("Unable to create a producer session: " + e.getMessage());
            failure.initCause(e);
            throw failure;
        } catch (JMSException | RuntimeException e) {
            throw e;
        } catch (SessionCreationError e) {
            throw e.error;
        } catch (Exception e) {
            JMSException failure = new JMSException("Unable to create a producer session: " + e.getMessage());
            failure.initCause(e);
            throw failure;
        } finally {
            if (!done) {
                permits.release();
            }
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
            // a session is kept above maxIdle only for the sends that wait for one
            if (broken || (!permits.hasQueuedThreads() && sessions.getNumIdle(mode) >= maxIdle)) {
                sessions.invalidateObject(mode, session);
            } else {
                sessions.returnObject(mode, session);
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to return a producer session: {}", e.getMessage(), e);
        } finally {
            permits.release();
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
     * Evicts the oldest idle sessions of a session mode above the maximum number of idle sessions.
     *
     * @param maxIdle The maximum number of idle sessions of each session mode
     */
    private record TrimPolicy(int maxIdle) implements EvictionPolicy<Session> {

        @Override
        public boolean evict(EvictionConfig config, PooledObject<Session> underTest, int idleCount) {
            return idleCount > maxIdle;
        }
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
        // the connection being opened, outside the lock, which the other sends that need one wait for
        private @Nullable CompletableFuture<PoolConnection> connecting;
        private boolean closed;

        private SessionFactory(JMSConnectionPool connectionPool) {
            this.connectionPool = connectionPool;
        }

        @Override
        public PooledObject<Session> makeObject(SessionMode mode) throws Exception {
            try {
                return createPooledSession(mode);
            } catch (Error e) {
                // pool2 only gives back its count of the object being created for an Exception, so an Error would
                // take one of the maximum sessions for good: borrow throws the Error itself again
                throw new SessionCreationError(e);
            }
        }

        private PooledSession createPooledSession(SessionMode mode) throws JMSException {
            PoolConnection connection = openSession();
            try {
                return createSession(connection, mode);
            } catch (JMSException | RuntimeException e) {
                // the connection may have failed: the next attempt uses a new one
                LOGGER.debug("Unable to create a producer session, retrying on a new connection: {}", e.getMessage());
                sessionClosed(connection);
                replace(connection);
                PoolConnection retry = openSession();
                try {
                    return createSession(retry, mode);
                } catch (JMSException | RuntimeException retryFailure) {
                    sessionClosed(retry);
                    retryFailure.addSuppressed(e);
                    throw retryFailure;
                }
            }
        }

        private PooledSession createSession(PoolConnection connection, SessionMode mode) throws JMSException {
            try {
                return new PooledSession(connection.connection.createSession(mode.transacted(), mode.acknowledgeMode()), connection);
            } catch (Error e) {
                // the session counted on the connection was not created
                sessionClosed(connection);
                throw e;
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
         * Opens the connection without holding the lock, so that releasing sessions and closing the pool do not wait
         * for a connect.
         *
         * @return The current connection, opened if there is none, counting the session about to be created on it
         */
        private PoolConnection openSession() throws JMSException {
            while (true) {
                CompletableFuture<PoolConnection> pending;
                CompletableFuture<PoolConnection> opening = null;
                synchronized (this) {
                    if (closed) {
                        throw new JMSException("The producer session pool is closed");
                    }
                    if (current != null) {
                        current.openSessions++;
                        return current;
                    }
                    pending = connecting;
                    if (pending == null) {
                        opening = new CompletableFuture<>();
                        connecting = opening;
                    }
                }
                if (opening != null) {
                    connect(opening);
                } else {
                    await(pending);
                }
                // counts the session on the connection, if it is still the current one
            }
        }

        private void connect(CompletableFuture<PoolConnection> opening) throws JMSException {
            PoolConnection connection = null;
            boolean installed;
            try {
                connection = new PoolConnection(connectionPool.getConnectionFactory().createConnection());
                listen(connection);
                synchronized (this) {
                    connecting = null;
                    // the pool may have been closed while connecting
                    installed = !closed && current == null;
                    if (installed) {
                        connections.add(connection);
                        current = connection;
                    }
                }
            } catch (Throwable e) {
                // an Error too, such as an OutOfMemoryError when the provider can't start a thread, which is often
                // temporary: the sends waiting for this connect fail, and the next send connects again
                synchronized (this) {
                    connecting = null;
                }
                if (connection != null) {
                    connection.closed = true;
                    closeQuietly(connection);
                }
                opening.completeExceptionally(e);
                throw e;
            }
            if (!installed) {
                connection.closed = true;
                closeQuietly(connection);
            }
            opening.complete(connection);
        }

        private void await(CompletableFuture<PoolConnection> pending) throws JMSException {
            try {
                pending.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                JMSException failure = new JMSException("Interrupted while opening the producer connection");
                failure.initCause(e);
                throw failure;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof JMSException jmsException) {
                    throw jmsException;
                }
                JMSException failure = new JMSException("Unable to open the producer connection: " + e.getCause().getMessage());
                failure.initCause(e.getCause());
                throw failure;
            }
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
            closeQuietly(connection);
        }

        private void closeQuietly(PoolConnection connection) {
            try {
                connection.connection.close();
            } catch (JMSException | RuntimeException e) {
                LOGGER.debug("Unable to close the producer connection: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Carries an {@link Error} thrown while creating a session through pool2 as an {@link Exception}.
     */
    private static final class SessionCreationError extends Exception {

        @java.io.Serial
        private static final long serialVersionUID = 1L;

        private final transient Error error;

        SessionCreationError(Error error) {
            super(error);
            this.error = error;
        }
    }
}
