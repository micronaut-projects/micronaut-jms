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

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.jms.configuration.properties.JMSListenerRecoveryConfiguration;
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
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import java.util.ArrayList;
import java.util.Collection;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Registry for all {@link JMSListener}s managed by Micronaut JMS. Listeners can be dynamically registered
 *  using the {@link JMSListenerRegistry#register(Connection, JMSDestinationType, String, boolean, int, MessageListener, ExecutorService, boolean, Optional)}
 *  method. When the application context closes, all open connections, listeners, and sessions, are closed.
 *
 * <p>The registry listens for the errors that the JMS provider reports on the connections of the listeners it creates.
 * After an error, each listener of the connection checks its session, and one whose session no longer works closes
 * its session and consumer and creates them again on the same connection, retrying with a growing delay as
 * {@link JMSListenerRecoveryConfiguration configured} until it succeeds or the listener shuts down.</p>
 *
 * @author Elliott Pope
 * @since 2.1.1
 */
@Singleton
public class JMSListenerRegistry {

    /**
     * The {@link JMSListenerRecoveryConfiguration} defaults, for a registry created without a configuration.
     */
    static final JMSListenerRecoveryConfiguration DEFAULT_RECOVERY = new JMSListenerRecoveryConfiguration() {
        @Override
        public boolean isEnabled() {
            return JMSListenerRecoveryConfiguration.DEFAULT_ENABLED;
        }

        @Override
        public int getWarnAttempts() {
            return JMSListenerRecoveryConfiguration.DEFAULT_WARN_ATTEMPTS;
        }

        @Override
        public Duration getInitialDelay() {
            return Duration.parse(JMSListenerRecoveryConfiguration.DEFAULT_INITIAL_DELAY);
        }

        @Override
        public Duration getMaxDelay() {
            return Duration.parse(JMSListenerRecoveryConfiguration.DEFAULT_MAX_DELAY);
        }
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSListenerRegistry.class);
    private final Set<JMSListener> listeners = Collections.synchronizedSet(new HashSet<>());
    private final Map<JMSListener, PooledObject<?>> pooledConnections = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<JMSListener, Connection> dedicatedConnections = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Collection<GlobalJMSListenerSuccessHandler> globalSuccessHandlers;
    private final Collection<GlobalJMSListenerErrorHandler> globalErrorHandlers;
    private final Collection<JMSMessageObserver> observers;
    private final JMSListenerRecoveryConfiguration recoveryConfiguration;
    private final Map<Connection, ConnectionErrors> connectionErrors = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<JMSListener> recovering = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private @Nullable ScheduledExecutorService recoveryExecutor;
    private boolean shutdown;

    /**
     * @param globalSuccessHandlers The success handlers of every listener
     * @param globalErrorHandlers   The error handlers of every listener
     * @deprecated Use {@link #JMSListenerRegistry(Collection, Collection, Collection, JMSListenerRecoveryConfiguration)} instead
     */
    @Deprecated(since = "5.2.0", forRemoval = true)
    public JMSListenerRegistry(
        Collection<GlobalJMSListenerSuccessHandler> globalSuccessHandlers,
        Collection<GlobalJMSListenerErrorHandler> globalErrorHandlers) {
        this(globalSuccessHandlers, globalErrorHandlers, List.of(), DEFAULT_RECOVERY);
    }

    /**
     * @param globalSuccessHandlers The success handlers of every listener
     * @param globalErrorHandlers   The error handlers of every listener
     * @param observers             The observers of the messages of every listener
     * @param recoveryConfiguration How listeners recover from a failed session
     * @throws ConfigurationException if a recovery delay isn't positive, the maximum delay is shorter than the
     *                                initial delay, or the number of warning attempts is less than 1
     * @since 5.2.0
     */
    @Inject
    public JMSListenerRegistry(
        Collection<GlobalJMSListenerSuccessHandler> globalSuccessHandlers,
        Collection<GlobalJMSListenerErrorHandler> globalErrorHandlers,
        Collection<JMSMessageObserver> observers,
        JMSListenerRecoveryConfiguration recoveryConfiguration) {
        this.globalSuccessHandlers = globalSuccessHandlers;
        this.globalErrorHandlers = globalErrorHandlers;
        this.observers = observers;
        this.recoveryConfiguration = validate(recoveryConfiguration);
    }

    private static JMSListenerRecoveryConfiguration validate(JMSListenerRecoveryConfiguration configuration) {
        String prefix = JMSListenerRecoveryConfiguration.PREFIX;
        Duration initialDelay = configuration.getInitialDelay();
        Duration maxDelay = configuration.getMaxDelay();
        // retries never stop, so a delay that isn't positive would retry back to back or overflow
        if (initialDelay == null || initialDelay.isNegative() || initialDelay.isZero()) {
            throw new ConfigurationException(prefix + ".initial-delay must be positive, but is " + initialDelay);
        }
        if (maxDelay == null || maxDelay.compareTo(initialDelay) < 0) {
            throw new ConfigurationException(prefix + ".max-delay must not be shorter than " + prefix
                + ".initial-delay (" + initialDelay + "), but is " + maxDelay);
        }
        if (configuration.getWarnAttempts() < 1) {
            throw new ConfigurationException(prefix + ".warn-attempts must be at least 1, but is " + configuration.getWarnAttempts());
        }
        return configuration;
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
        if (recoveryConfiguration.isEnabled()) {
            listener.setSessionFactory(() -> connection.createSession(transacted, acknowledgeMode));
            listenForErrors(connection, listener);
        }
        return listener;
    }

    /**
     * Adds the listener to those that check their session when the provider reports an error on the connection. The
     * first listener of a connection sets its exception listener, which still forwards the errors to the exception
     * listener that was set before.
     */
    private void listenForErrors(Connection connection, JMSListener listener) {
        ConnectionErrors errors;
        synchronized (connectionErrors) {
            errors = connectionErrors.get(connection);
            if (errors == null) {
                try {
                    errors = new ConnectionErrors(connection, connection.getExceptionListener());
                    connection.setExceptionListener(errors);
                } catch (JMSException | RuntimeException e) {
                    LOGGER.debug("The JMS provider does not report the errors of connection {}, so the listener of {} "
                        + "can't recover a failed session: {}", connection, listener, e.getMessage());
                    return;
                }
                connectionErrors.put(connection, errors);
            }
            errors.consumers.add(listener);
        }
    }

    /**
     * Removes the listener from those that check their session when the provider reports an error on the connection,
     * and gives the connection its previous exception listener back once it has no more listeners.
     */
    private void stopListeningForErrors(JMSListener listener) {
        recovering.remove(listener);
        synchronized (connectionErrors) {
            for (ConnectionErrors errors : connectionErrors.values()) {
                if (errors.consumers.remove(listener) && errors.consumers.isEmpty()) {
                    connectionErrors.remove(errors.connection);
                    errors.restore();
                    return;
                }
            }
        }
    }

    /**
     * Checks the session of the listener, unless a previous attempt already found it broken, and recreates it. A
     * failed attempt is retried after {@link #delay(int)}, without limit: the provider may not report another error,
     * since Oracle AQ, for example, only checks the sessions that are still open, so a listener that stopped retrying
     * would never receive messages again.
     *
     * @param listener the listener
     * @param error    the error that the provider reported
     * @param attempt  the attempt, from 1
     */
    void recover(JMSListener listener, JMSException error, int attempt) {
        if (!listeners.contains(listener)) {
            recovering.remove(listener);
            return;
        }
        if (attempt == 1) {
            if (!listener.isSessionBroken()) {
                // for example a rejected asynchronous send, which ActiveMQ reports on a connection that still works
                LOGGER.debug("The session of the listener of {} still works after the JMS provider reported: {}",
                    listener, error.getMessage());
                recovering.remove(listener);
                return;
            }
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("The session of the listener of {} failed after the JMS provider reported: {}. Recreating it",
                    listener, describe(error));
            }
        }
        try {
            if (listener.recreateSession()) {
                LOGGER.info("Recreated the session of the listener of {} (attempt {})", listener, attempt);
            }
            recovering.remove(listener);
        } catch (JMSException | RuntimeException e) {
            Duration delay = delay(attempt);
            if (attempt <= recoveryConfiguration.getWarnAttempts()) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Unable to recreate the session of the listener of {} (attempt {}), retrying in {}: {}",
                        listener, attempt, delay, describe(e));
                }
            } else if (attempt == recoveryConfiguration.getWarnAttempts() + 1 || LOGGER.isDebugEnabled()) {
                // the stack trace once, since the attempts continue for as long as the provider fails
                LOGGER.error("Unable to recreate the session of the listener of {} (attempt {}). It receives no messages "
                    + "until the session is recreated, retrying in {}", listener, attempt, delay, e);
            } else if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Unable to recreate the session of the listener of {} (attempt {}). It receives no messages "
                    + "until the session is recreated, retrying in {}: {}", listener, attempt, delay, describe(e));
            }
            schedule(() -> recover(listener, error, attempt + 1), delay, listener);
        }
    }

    /**
     * @param attempt the attempt that failed, from 1
     * @return the delay before the next attempt: the initial delay, doubled after each attempt up to the maximum
     */
    Duration delay(int attempt) {
        Duration initial = recoveryConfiguration.getInitialDelay();
        Duration max = recoveryConfiguration.getMaxDelay();
        Duration delay = initial;
        for (int i = 1; i < attempt && delay.compareTo(max) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(max) > 0 ? max : delay;
    }

    private void schedule(Runnable recovery, Duration delay, JMSListener listener) {
        try {
            recoveryExecutor().schedule(recovery, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException _) {
            // the registry shut down
            recovering.remove(listener);
        }
    }

    private synchronized ScheduledExecutorService recoveryExecutor() {
        if (shutdown) {
            throw new RejectedExecutionException("The listener registry shut down");
        }
        if (recoveryExecutor == null) {
            recoveryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "jms-listener-recovery");
                thread.setDaemon(true);
                return thread;
            });
        }
        return recoveryExecutor;
    }

    private static String describe(Exception error) {
        Throwable cause = error instanceof JMSException jmsException && jmsException.getLinkedException() != null
            ? jmsException.getLinkedException()
            : error.getCause();
        return cause == null || cause == error ? String.valueOf(error) : error + " (caused by " + cause + ")";
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
            stopListeningForErrors(listener);
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
        synchronized (this) {
            shutdown = true;
            if (recoveryExecutor != null) {
                recoveryExecutor.shutdownNow();
                recoveryExecutor = null;
            }
        }
        Set<PooledObject<?>> stopped = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<PooledObject<?>> failed = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Connection> dedicated = Collections.newSetFromMap(new IdentityHashMap<>());
        listeners.forEach(listener -> stop(listener, stopped, failed, dedicated));
        listeners.clear();
        pooledConnections.clear();
        dedicatedConnections.clear();
        recovering.clear();
        List<ConnectionErrors> listened;
        synchronized (connectionErrors) {
            listened = new ArrayList<>(connectionErrors.values());
            connectionErrors.clear();
        }
        listened.forEach(ConnectionErrors::restore);
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

    private void stop(JMSListener listener, Set<PooledObject<?>> stopped, Set<PooledObject<?>> failed, Set<Connection> dedicated) {
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
    }

    /**
     * The exception listener of a connection, which forwards the errors to the exception listener that was set before
     * and lets the listeners of the connection check their session.
     */
    private final class ConnectionErrors implements ExceptionListener {

        private final Connection connection;
        private final @Nullable ExceptionListener previous;
        private final Set<JMSListener> consumers = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

        private ConnectionErrors(Connection connection, @Nullable ExceptionListener previous) {
            this.connection = connection;
            this.previous = previous;
        }

        @Override
        public void onException(JMSException exception) {
            try {
                if (previous != null) {
                    previous.onException(exception);
                }
            } finally {
                checkSessions(exception);
            }
        }

        private void checkSessions(JMSException error) {
            List<JMSListener> affected;
            synchronized (consumers) {
                affected = new ArrayList<>(consumers);
            }
            for (JMSListener listener : affected) {
                if (recovering.add(listener)) {
                    // off the thread of the provider, which may be the one that delivers messages to the listener
                    schedule(() -> recover(listener, error, 1), Duration.ZERO, listener);
                }
            }
        }

        private void restore() {
            try {
                connection.setExceptionListener(previous);
            } catch (JMSException | RuntimeException e) {
                LOGGER.debug("Unable to restore the exception listener of connection {}: {}", connection, e.getMessage());
            }
        }
    }
}
