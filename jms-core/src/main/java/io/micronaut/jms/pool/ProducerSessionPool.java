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
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Reuses the sessions of producers across sends, instead of opening a connection and a session for each message.
 * The sessions are created on one connection from the connection factory of a {@link JMSConnectionPool}, which this
 * pool opens on the first send and closes with itself. A session is used by one send at a time, and a session that
 * failed is closed rather than reused. When a session cannot be created, the connection is replaced.
 *
 * @since 5.2.0
 */
public final class ProducerSessionPool implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerSessionPool.class);

    private final JMSConnectionPool connectionPool;
    private final GenericKeyedObjectPool<SessionMode, Session> sessions;
    private final Map<Session, SessionMode> borrowed = Collections.synchronizedMap(new IdentityHashMap<>());
    private @Nullable Connection connection;

    /**
     * @param connectionPool The pool of the connection that the sessions are created on
     * @param maxIdle        The maximum number of idle sessions kept for each session mode
     */
    public ProducerSessionPool(JMSConnectionPool connectionPool, int maxIdle) {
        this.connectionPool = connectionPool;
        GenericKeyedObjectPoolConfig<Session> config = new GenericKeyedObjectPoolConfig<>();
        // as many sessions as concurrent sends, of which maxIdle are kept
        config.setMaxTotalPerKey(-1);
        config.setMaxTotal(-1);
        config.setMaxIdlePerKey(maxIdle);
        config.setJmxEnabled(false);
        this.sessions = new GenericKeyedObjectPool<>(new SessionFactory(), config);
    }

    /**
     * Borrows a session, which must be returned with {@link #release(Session, boolean)}.
     *
     * @param transacted      Whether the session is transacted
     * @param acknowledgeMode The acknowledge mode of the session
     * @return The session
     * @throws JMSException If the session cannot be created
     */
    public Session borrow(boolean transacted, int acknowledgeMode) throws JMSException {
        SessionMode mode = new SessionMode(transacted, acknowledgeMode);
        try {
            Session session = sessions.borrowObject(mode);
            borrowed.put(session, mode);
            return session;
        } catch (JMSException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            JMSException failure = new JMSException("Unable to create a producer session: " + e.getMessage());
            failure.initCause(e);
            throw failure;
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
     * Closes the idle sessions and the connection, which also closes the sessions that are still borrowed.
     */
    @Override
    public void close() {
        sessions.close();
        resetConnection();
    }

    private synchronized Connection connection() throws JMSException {
        if (connection == null) {
            connection = connectionPool.getConnectionFactory().createConnection();
        }
        return connection;
    }

    private synchronized void resetConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException | RuntimeException e) {
                LOGGER.debug("Unable to close the producer connection: {}", e.getMessage(), e);
            }
            connection = null;
        }
    }

    private record SessionMode(boolean transacted, int acknowledgeMode) {
    }

    private final class SessionFactory extends BaseKeyedPooledObjectFactory<SessionMode, Session> {

        @Override
        public Session create(SessionMode mode) throws JMSException {
            try {
                return connection().createSession(mode.transacted(), mode.acknowledgeMode());
            } catch (JMSException | RuntimeException e) {
                // the connection may have failed: the next attempt uses a new one
                LOGGER.debug("Unable to create a producer session, retrying on a new connection: {}", e.getMessage());
                resetConnection();
                try {
                    return connection().createSession(mode.transacted(), mode.acknowledgeMode());
                } catch (JMSException | RuntimeException retryFailure) {
                    retryFailure.addSuppressed(e);
                    throw retryFailure;
                }
            }
        }

        @Override
        public org.apache.commons.pool2.PooledObject<Session> wrap(Session session) {
            return new DefaultPooledObject<>(session);
        }

        @Override
        public void destroyObject(SessionMode mode, org.apache.commons.pool2.PooledObject<Session> pooled) throws JMSException {
            pooled.getObject().close();
        }
    }
}
