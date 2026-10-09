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
import io.micronaut.jms.pool.PooledObject;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
    private final Collection<GlobalJMSListenerSuccessHandler> globalSuccessHandlers;
    private final Collection<GlobalJMSListenerErrorHandler> globalErrorHandlers;

    public JMSListenerRegistry(
        Collection<GlobalJMSListenerSuccessHandler> globalSuccessHandlers,
        Collection<GlobalJMSListenerErrorHandler> globalErrorHandlers) {
        this.globalSuccessHandlers = globalSuccessHandlers;
        this.globalErrorHandlers = globalErrorHandlers;
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
        connection.start();
        Session session = connection.createSession(transacted, acknowledgeMode);
        JMSListener listener = new JMSListener(session, delegate, destinationType, destination, executor, messageSelector);
        if (CollectionUtils.isNotEmpty(globalSuccessHandlers)) {
            listener.addSuccessHandlers(globalSuccessHandlers);
        }
        if (CollectionUtils.isNotEmpty(globalErrorHandlers)) {
            listener.addErrorHandlers(globalErrorHandlers);
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
        if (connection instanceof PooledObject<?> pooled) {
            // returned to its pool once its listener stops, so that every re-registration, also against a pool that
            // outlives this registry in development mode, can lend it again
            pooledConnections.put(listener, pooled);
        }
        return listener;
    }

    /**
     * Shuts down all registered {@link JMSListener}s and returns the pooled connections they were
     * registered with to their pool. If a listener fails to shut down then it is logged and skipped, and its
     * connection is not returned to the pool, since it may still hold the listener's session.
     */
    @PreDestroy
    public void shutdown() {
        Set<PooledObject<?>> stopped = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<PooledObject<?>> failed = Collections.newSetFromMap(new IdentityHashMap<>());
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
        });
        listeners.clear();
        pooledConnections.clear();
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
