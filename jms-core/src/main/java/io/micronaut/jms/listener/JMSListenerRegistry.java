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
package io.micronaut.jms.listener;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.observation.JMSMessageObserver;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.pool.PooledObject;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Registry for all {@link JMSListener}s managed by Micronaut JMS. Listeners can be dynamically registered
 *  using the {@link JMSListenerRegistry#register(Connection, JMSDestinationType, String, boolean, int, MessageListener, ExecutorService, boolean, Optional)}
 *  method. When the application context closes, all open connections, listeners, and sessions, are closed.
 *
 * @author Elliott Pope
 * @since 2.1.1
 */
@Singleton
public class JMSListenerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSListenerRegistry.class);
    private final Set<JMSListener> listeners = Collections.synchronizedSet(new HashSet<>());
    private final Map<JMSListener, PooledObject<?>> pooledConnections = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<JMSListener, Connection> dedicatedConnections = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Collection<GlobalJMSListenerSuccessHandler> globalSuccessHandlers;
    private final Collection<GlobalJMSListenerErrorHandler> globalErrorHandlers;
    private final Collection<JMSMessageObserver> observers;

    /**
     * @param globalSuccessHandlers The success handlers of every listener
     * @param globalErrorHandlers   The error handlers of every listener
     * @deprecated Use {@link #JMSListenerRegistry(Collection, Collection, Collection)} instead
     */
    @Deprecated(since = "5.2.0", forRemoval = true)
    public JMSListenerRegistry(
        Collection<GlobalJMSListenerSuccessHandler> globalSuccessHandlers,
        Collection<GlobalJMSListenerErrorHandler> globalErrorHandlers) {
        this(globalSuccessHandlers, globalErrorHandlers, List.of());
    }

    /**
     * @param globalSuccessHandlers The success handlers of every listener
     * @param globalErrorHandlers   The error handlers of every listener
     * @param observers             The observers of the messages of every listener
     * @since 5.2.0
     */
    @Inject
    public JMSListenerRegistry(
        Collection<GlobalJMSListenerSuccessHandler> globalSuccessHandlers,
        Collection<GlobalJMSListenerErrorHandler> globalErrorHandlers,
        Collection<JMSMessageObserver> observers) {
        this.globalSuccessHandlers = globalSuccessHandlers;
        this.globalErrorHandlers = globalErrorHandlers;
        this.observers = observers;
    }

    /**
     * Registers a new listener to be managed by Micronaut JMS.
     *
     * @param listener - the listener to be registered
     * @param autoStart - whether the listener should be automatically started when registered
     * @throws JMSException - if the listener fails to start
     */
    public void register(JMSListener listener, boolean autoStart) throws JMSException {
        if (autoStart) {
            listener.start();
        }
        listeners.add(listener);
    }

    /**
     * Creates and registers a new listener to be managed by Micronaut JMS.
     *
     * @param connection - the {@link Connection} the listener will be linked to
     * @param destinationType - the {@link JMSDestinationType} of the target destination
     * @param destination - the name of the target destination
     * @param transacted - whether the listener should commit the transaction once the message is received
     * @param acknowledgeMode - whether the message receipt should be acknowledged
     * @param delegate - the underlying handler to delegate to
     * @param executor - the {@link ExecutorService} to perform the message handling logic on.
     * @param autoStart -  whether the listener should be automatically started when registered
     * @param messageSelector - the message selector for the listener
     * @return the listener that has been registered
     * @throws JMSException - if the listener fails to start
     */
    public JMSListener register(
            Connection connection,
            JMSDestinationType destinationType,
            String destination,
            final boolean transacted,
            final int acknowledgeMode,
            MessageListener delegate,
            ExecutorService executor,
            boolean autoStart,
            Optional<String> messageSelector) throws JMSException {
        return register(connection, destinationType, destination, transacted, acknowledgeMode, delegate, executor, autoStart, messageSelector, null);
    }

    /**
     * Creates and registers a new listener to be managed by Micronaut JMS, optionally with a named topic
     * subscription. When the subscription {@link JMSTopicSubscription#requiresDedicatedConnection() requires a
     * dedicated connection}, the given connection must not be pooled: the registry closes it when the
     * listener shuts down. If registration fails, the caller still owns the connection and must close it.
     *
     * @param connection - the {@link Connection} the listener will be linked to
     * @param destinationType - the {@link JMSDestinationType} of the target destination
     * @param destination - the name of the target destination
     * @param transacted - whether the listener should commit the transaction once the message is received
     * @param acknowledgeMode - whether the message receipt should be acknowledged
     * @param delegate - the underlying handler to delegate to
     * @param executor - the {@link ExecutorService} to perform the message handling logic on.
     * @param autoStart -  whether the listener should be automatically started when registered
     * @param messageSelector - the message selector for the listener
     * @param subscription - the named topic subscription, or {@code null}
     * @return the listener that has been registered
     * @throws JMSException - if the listener fails to start
     * @since 5.2.0
     */
    @SuppressWarnings("java:S107") // extends the existing register overload with the subscription
    public JMSListener register(
            Connection connection,
            JMSDestinationType destinationType,
            String destination,
            final boolean transacted,
            final int acknowledgeMode,
            MessageListener delegate,
            ExecutorService executor,
            boolean autoStart,
            Optional<String> messageSelector,
            @Nullable JMSTopicSubscription subscription) throws JMSException {
        connection.start();
        Session session = connection.createSession(transacted, acknowledgeMode);
        JMSListener listener;
        try {
            // the listener rejects some combinations, such as a subscription on a queue, which must close the session too
            listener = new JMSListener(session, delegate, destinationType, destination, executor, messageSelector, subscription);
            if (CollectionUtils.isNotEmpty(globalSuccessHandlers)) {
                listener.addSuccessHandlers(globalSuccessHandlers);
            }
            if (CollectionUtils.isNotEmpty(globalErrorHandlers)) {
                listener.addErrorHandlers(globalErrorHandlers);
            }
            if (CollectionUtils.isNotEmpty(observers)) {
                listener.addObservers(observers);
            }
            if (transacted) {
                listener.addSuccessHandlers(new TransactionalJMSListenerSuccessHandler());
                listener.addErrorHandlers(new TransactionalJMSListenerErrorHandler());
            }
            if (acknowledgeMode == Session.CLIENT_ACKNOWLEDGE) {
                listener.addSuccessHandlers(new AcknowledgingJMSListenerSuccessHandler());
            }
            listener.addErrorHandlers(new LoggingJMSListenerErrorHandler());
            this.register(listener, autoStart);
        } catch (JMSException | RuntimeException e) {
            try {
                session.close();
            } catch (JMSException | RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
        if (subscription != null && subscription.requiresDedicatedConnection() && !(connection instanceof PooledObject<?>)) {
            // only once the listener has started: the caller closes the connection if registration fails
            dedicatedConnections.put(listener, connection);
        }
        if (connection instanceof PooledObject<?> pooled) {
            // returned to its pool on shutdown, otherwise every re-registration takes another pooled connection
            pooledConnections.put(listener, pooled);
        }
        return listener;
    }

    /**
     * Creates and registers a new listener to be managed by Micronaut JMS, optionally with a named topic
     * subscription, on a connection the registry opens. The connection comes from the pool, unless the
     * subscription {@link JMSTopicSubscription#requiresDedicatedConnection() requires a dedicated connection}:
     * then it is a new connection from the pool's factory with the subscription's client ID, which the registry
     * closes when the listener shuts down or when registration fails. A pooled connection is returned to its pool
     * when registration fails.
     *
     * @param connectionPool - the pool that provides the listener's connection
     * @param destinationType - the {@link JMSDestinationType} of the target destination
     * @param destination - the name of the target destination
     * @param transacted - whether the listener should commit the transaction once the message is received
     * @param acknowledgeMode - whether the message receipt should be acknowledged
     * @param delegate - the underlying handler to delegate to
     * @param executor - the {@link ExecutorService} to perform the message handling logic on.
     * @param autoStart -  whether the listener should be automatically started when registered
     * @param messageSelector - the message selector for the listener
     * @param subscription - the named topic subscription, or {@code null}
     * @return the listener that has been registered
     * @throws JMSException - if the connection cannot be opened or the listener fails to start
     * @since 5.2.0
     */
    @SuppressWarnings("java:S107") // extends the existing register overload with the subscription
    public JMSListener register(
            JMSConnectionPool connectionPool,
            JMSDestinationType destinationType,
            String destination,
            final boolean transacted,
            final int acknowledgeMode,
            MessageListener delegate,
            ExecutorService executor,
            boolean autoStart,
            Optional<String> messageSelector,
            @Nullable JMSTopicSubscription subscription) throws JMSException {
        return register(connectionPool, destinationType, destination, transacted, acknowledgeMode, delegate, executor,
            autoStart, messageSelector, subscription, 1).get(0);
    }

    /**
     * Creates and registers the consumers of a destination, each a listener with its own session, on one
     * connection that the registry opens: a connection from the pool, unless the subscription
     * {@link JMSTopicSubscription#requiresDedicatedConnection() requires a dedicated connection}, in which case it
     * is a new connection from the pool's factory with the subscription's client ID. All the consumers share that
     * connection, so they take a single pooled connection however many there are.
     *
     * <p>Registration stops at the first consumer that fails: the consumers already registered are stopped, the
     * connection is returned to its pool or closed, and the failure is thrown, so that either every consumer is
     * registered or none is.</p>
     *
     * @param connectionPool - the pool that provides the listeners' connection
     * @param destinationType - the {@link JMSDestinationType} of the target destination
     * @param destination - the name of the target destination
     * @param transacted - whether the listener should commit the transaction once the message is received
     * @param acknowledgeMode - whether the message receipt should be acknowledged
     * @param delegate - the underlying handler to delegate to, called by every consumer
     * @param executor - the {@link ExecutorService} to perform the message handling logic on.
     * @param autoStart -  whether the listeners should be automatically started when registered
     * @param messageSelector - the message selector for the listeners
     * @param subscription - the named topic subscription, or {@code null}
     * @param consumers - the number of consumers, at least 1
     * @return the listeners that have been registered, one per consumer
     * @throws JMSException - if the connection cannot be opened or a listener fails to start
     * @throws IllegalArgumentException - if {@code consumers} is less than 1
     * @since 5.2.0
     */
    @SuppressWarnings("java:S107") // extends the register overload with the number of consumers
    public List<JMSListener> register(
            JMSConnectionPool connectionPool,
            JMSDestinationType destinationType,
            String destination,
            final boolean transacted,
            final int acknowledgeMode,
            MessageListener delegate,
            ExecutorService executor,
            boolean autoStart,
            Optional<String> messageSelector,
            @Nullable JMSTopicSubscription subscription,
            int consumers) throws JMSException {
        if (consumers < 1) {
            throw new IllegalArgumentException("The listener of " + destination + " must have at least one consumer, but consumers = " + consumers);
        }
        Connection connection = openConnection(connectionPool, subscription);
        List<JMSListener> registered = new ArrayList<>(consumers);
        try {
            for (int i = 0; i < consumers; i++) {
                registered.add(register(connection, destinationType, destination, transacted, acknowledgeMode,
                    delegate, executor, autoStart, messageSelector, subscription));
            }
            return registered;
        } catch (JMSException | RuntimeException e) {
            unregister(registered, connection, e);
            throw e;
        }
    }

    private static Connection openConnection(JMSConnectionPool connectionPool,
                                             @Nullable JMSTopicSubscription subscription) throws JMSException {
        if (subscription == null || !subscription.requiresDedicatedConnection()) {
            return connectionPool.createConnection();
        }
        // the client ID must be set before the connection is used, so it can't come from the pool
        Connection connection = connectionPool.getConnectionFactory().createConnection();
        try {
            connection.setClientID(subscription.effectiveClientId());
            return connection;
        } catch (JMSException | RuntimeException e) {
            close(connection, e);
            throw e;
        }
    }

    /**
     * Stops the given listeners after a failed registration and gives back their connection. As on shutdown, a
     * pooled connection stays out of the pool if a listener fails to stop, since it may still hold its session.
     */
    private void unregister(List<JMSListener> registered, Connection connection, Exception failure) {
        boolean stopped = true;
        for (JMSListener listener : registered) {
            listeners.remove(listener);
            pooledConnections.remove(listener);
            dedicatedConnections.remove(listener);
            try {
                listener.stop();
            } catch (JMSException | RuntimeException stopFailure) {
                failure.addSuppressed(stopFailure);
                stopped = false;
            }
        }
        if (stopped || !(connection instanceof PooledObject<?>)) {
            close(connection, failure);
        }
    }

    private static void close(Connection connection, Exception failure) {
        try {
            connection.close();
        } catch (JMSException | RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    /**
     * Shuts down all registered {@link JMSListener}s, returns the pooled connections they were registered with
     * to their pool and closes their dedicated connections, each connection once however many listeners share it.
     * If a listener fails to shut down then it is logged and skipped, and its pooled connection is not returned
     * to the pool, since it may still hold the listener's session.
     */
    @PreDestroy
    public void shutdown() {
        Set<PooledObject<?>> stopped = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<PooledObject<?>> failed = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Connection> dedicated = Collections.newSetFromMap(new IdentityHashMap<>());
        listeners.forEach(listener -> {
            PooledObject<?> connection = pooledConnections.get(listener);
            try {
                listener.stop();
                if (connection != null) {
                    stopped.add(connection);
                }
            } catch (JMSException e) {
                LOGGER.error("Failed to shutdown listener", e);
                if (connection != null) {
                    failed.add(connection);
                }
            }
            Connection dedicatedConnection = dedicatedConnections.get(listener);
            if (dedicatedConnection != null) {
                dedicated.add(dedicatedConnection);
            }
        });
        listeners.clear();
        pooledConnections.clear();
        dedicatedConnections.clear();
        for (Connection connection : dedicated) {
            try {
                connection.close();
            } catch (JMSException e) {
                LOGGER.error("Failed to close a dedicated listener connection", e);
            }
        }
        stopped.removeAll(failed);
        for (PooledObject<?> connection : stopped) {
            try {
                connection.close();
            } catch (JMSException e) {
                LOGGER.error("Failed to return a listener connection to its pool", e);
            }
        }
    }
}
