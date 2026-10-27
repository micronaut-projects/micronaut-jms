/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.messaging.exceptions.MessagingSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import java.util.concurrent.CompletableFuture;

/**
 * Decorator of the provided {@link ConnectionFactory} to ensure maximum reuse
 * of {@link Connection} instances.
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
public class JMSConnectionPool extends AbstractPool<PooledObject<Connection>> implements ConnectionFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSConnectionPool.class);

    private final ConnectionFactory connectionFactory;

    public JMSConnectionPool(ConnectionFactory connectionFactory,
                             int initialPoolSize,
                             int maxPoolSize) {
        super(initialPoolSize, maxPoolSize);
        this.connectionFactory = connectionFactory;
        for (int i = 0; i < initialPoolSize; i++) {
            CompletableFuture.runAsync(() -> pool.add(create()));
        }
    }

    private PooledConnection doCreate() {
        try {
            Connection connection = connectionFactory.createConnection();
            PooledConnection pooled = new PooledConnection(connection, this);
            listenForFailures(connection, pooled);
            connection.start(); // TODO config autostart
            return pooled;
        } catch (JMSException | RuntimeException e) {
            throw new MessagingSystemException("Problem creating pooled Connection", e);
        }
    }

    private static void listenForFailures(Connection connection, PooledConnection pooled) {
        try {
            // keeps a listener that the provider already set, such as the one of ActiveMQConnectionFactory#setExceptionListener
            pooled.setExceptionListener(connection.getExceptionListener());
            connection.setExceptionListener(pooled::onException);
        } catch (JMSException | RuntimeException e) {
            LOGGER.debug("The JMS provider does not report connection failures: {}", e.getMessage());
        }
    }

    @Override
    protected PooledConnection create(Object... args) {
        return doCreate();
    }

    private void doReset(PooledConnection pooledConnection) {
        // TODO
    }

    @Override
    protected void reset(PooledObject<Connection> pooledObject) {
        doReset(PooledConnection.of(pooledObject));
    }

    @Override
    protected boolean isValid(PooledObject<Connection> pooledObject) {
        return !PooledConnection.of(pooledObject).isBroken();
    }

    @Override
    protected void discard(PooledObject<Connection> pooledObject) {
        LOGGER.warn("Closing a JMS connection that the provider reported an error on: {}", pooledObject.get(),
            PooledConnection.of(pooledObject).getFailure());
        try {
            pooledObject.get().close();
        } catch (JMSException | RuntimeException e) {
            LOGGER.debug("Unable to close a failed JMS connection: {}", e.getMessage(), e);
        }
    }

    @Override
    public Connection createConnection() throws JMSException {
        return PooledConnection.of(request());
    }

    @Override
    public Connection createConnection(String userName,
                                       String password) throws JMSException {
        throw new UnsupportedOperationException("Cannot request a Connection with credentials. " +
            "All credentials must be configured in the ConnectionFactory");
    }

    @Override
    public JMSContext createContext() {
        return connectionFactory.createContext();
    }

    @Override
    public JMSContext createContext(String userName,
                                    String password) {
        return connectionFactory.createContext(userName, password);
    }

    @Override
    public JMSContext createContext(String userName,
                                    String password,
                                    int sessionMode) {
        return connectionFactory.createContext(userName, password, sessionMode);
    }

    @Override
    public JMSContext createContext(int sessionMode) {
        return connectionFactory.createContext(sessionMode);
    }

    /**
     * Provides access to the underlying {@link ConnectionFactory}.
     *
     * @return the connection factory
     */
    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    @Override
    public String toString() {
        return "JMSConnectionPool{" +
            "initialSize=" + initialSize +
            ", maxSize=" + maxSize +
            ", connectionFactory=" + connectionFactory +
            '}';
    }
}
