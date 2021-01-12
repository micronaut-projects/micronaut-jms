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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import java.util.concurrent.CompletableFuture;

/**
 * Decorator of the provided {@link ConnectionFactory} to ensure maximum reuse
 * of {@link Connection} instances.
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
public class JMSConnectionPool extends AbstractPool<PooledObject<Connection>> implements ConnectionFactory {

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
            connection.start(); // TODO config autostart
            return new PooledConnection(connection, this);
        } catch (JMSException | RuntimeException e) {
            throw new MessagingSystemException("Problem creating pooled Connection", e);
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

    @Override
    public String toString() {
        return "JMSConnectionPool{" +
            "initialSize=" + initialSize +
            ", maxSize=" + maxSize +
            ", connectionFactory=" + connectionFactory +
            '}';
    }
}
