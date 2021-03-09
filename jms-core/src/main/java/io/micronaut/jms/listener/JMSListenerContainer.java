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
package io.micronaut.jms.listener;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.messaging.exceptions.MessageListenerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.jms.Session.AUTO_ACKNOWLEDGE;

/**
 * Sets up and manages {@link MessageListener}s created by the
 * {@link io.micronaut.jms.annotations.JMSListener} and
 * {@link io.micronaut.jms.configuration.AbstractJMSListenerMethodProcessor}
 * processing. There is support for programmatically instantiating listeners
 * however this has not been fully incorporated into a coherent API yet and has
 * not been fully tested. It is recommended to instead use the
 * {@link io.micronaut.jms.annotations.JMSListener} annotation on existing classes.
 *
 * @param <T> the {@link javax.jms.Message} object type to convert to before handling
 * @author Elliott Pope
 * @since 1.0.0
 */
public class JMSListenerContainer<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSListenerContainer.class);
    private static final long DEFAULT_KEEP_ALIVE_TIME = 5; // TODO configurable
    private static final int DEFAULT_EXECUTOR_QUEUE_SIZE = 10; // TODO configurable
    private static final boolean DEFAULT_TRANSACTED = false; // TODO configurable
    private static final int DEFAULT_ACKNOWLEDGE_MODE = AUTO_ACKNOWLEDGE; // TODO configurable

    private final Set<Connection> openConnections = new HashSet<>();
    private final JMSConnectionPool connectionPool;
    private final int threadPoolSize;
    private final int maxThreadPoolSize;
    private final JMSDestinationType type;

    /**
     * @param connectionPool the {@link JMSConnectionPool} to pull
     *                       {@link Connection}s from to create {@link MessageListener}s
     * @param type           either {@link JMSDestinationType#QUEUE} or {@link JMSDestinationType#TOPIC}.
     * @param threadPoolSize the pool size
     */
    public JMSListenerContainer(JMSConnectionPool connectionPool,
                                JMSDestinationType type,
                                int threadPoolSize) {
        this(connectionPool, type, threadPoolSize, threadPoolSize);
    }

    /**
     * @param connectionPool    the {@link JMSConnectionPool} to pull
     *                          {@link Connection}s from to create {@link MessageListener}s
     * @param type              either {@link JMSDestinationType#QUEUE} or {@link JMSDestinationType#TOPIC}.
     * @param threadPoolSize    the minimum core thread pool size
     * @param maxThreadPoolSize the maximum number of threads to use handling incoming requests
     */
    public JMSListenerContainer(JMSConnectionPool connectionPool,
                                JMSDestinationType type,
                                int threadPoolSize,
                                int maxThreadPoolSize) {
        ArgumentUtils.check(() -> maxThreadPoolSize >= threadPoolSize)
            .orElseFail("maxThreadPoolSize cannot be smaller than the threadPoolSize");

        this.connectionPool = connectionPool;
        this.type = type;
        this.threadPoolSize = threadPoolSize;
        this.maxThreadPoolSize = maxThreadPoolSize;
    }

    /**
     * Registers a {@link JMSListenerContainer} with default concurrency.
     * <p>
     * NOTE: this method is not recommended; instead use the annotation driven
     * {@link io.micronaut.jms.annotations.JMSListener}
     *
     * @param destination the queue or topic name
     * @param listener    the message handler
     * @param clazz       the message type
     */
    public void registerListener(String destination,
                                 MessageHandler<T> listener,
                                 Class<T> clazz) {
        try {
            final Connection connection = connectionPool.createConnection();
            final Session session = connection.createSession(DEFAULT_TRANSACTED, DEFAULT_ACKNOWLEDGE_MODE);
            openConnections.add(connection);
            final MessageConsumer consumer = session.createConsumer(
                lookupDestination(destination, session));
            consumer.setMessageListener(
                new MessageHandlerAdapter<>(
                    new ConcurrentMessageHandler<>(
                        listener,
                        new ThreadPoolExecutor(
                            threadPoolSize,
                            maxThreadPoolSize,
                            DEFAULT_KEEP_ALIVE_TIME,
                            SECONDS,
                            new LinkedBlockingQueue<>(DEFAULT_EXECUTOR_QUEUE_SIZE),
                            Executors.defaultThreadFactory())),
                    clazz));
            LOGGER.debug("registered {} listener {} for destination '{}' and class {}",
                type.name().toLowerCase(), listener, destination, clazz.getName());
        } catch (Exception e) {
            throw new MessageListenerException(
                "Problem registering a MessageConsumer for " + destination, e);
        }
    }

    /**
     * Internal method used by the {@link JMSListenerContainerFactory} for
     * registering new listeners.
     * <p>
     * NOTE: this method is used internally by the
     * {@link io.micronaut.jms.configuration.AbstractJMSListenerMethodProcessor}
     * and is not recommended for use. Instead the annotation driven
     * {@link io.micronaut.jms.annotations.JMSListener} is preferred.
     *
     * @param destination     the queue or topic name
     * @param listener        the message handler
     * @param clazz           the message type
     * @param transacted      indicates whether the session will use a local transaction
     * @param acknowledgeMode when transacted is false, indicates how messages
     *                        received by the session will be acknowledged
     * @param messageSelector the message selector for the listener
     * @see Session#AUTO_ACKNOWLEDGE
     * @see Session#CLIENT_ACKNOWLEDGE
     * @see Session#DUPS_OK_ACKNOWLEDGE
     */
    public void registerListener(String destination,
            MessageListener listener,
            Class<T> clazz, // TODO unused
            boolean transacted,
            int acknowledgeMode,
            Optional<String> messageSelector) {
        try {
            final Connection connection = connectionPool.createConnection();
            final Session session = connection.createSession(transacted, acknowledgeMode);
            openConnections.add(connection);
            MessageConsumer consumer;
            if (!messageSelector.isPresent()) {
                consumer = session.createConsumer(
                        lookupDestination(destination, session));
            } else {
                consumer = session.createConsumer(
                        lookupDestination(destination, session), messageSelector.get());
            }

            consumer.setMessageListener((message) -> {
                try {
                    listener.onMessage(message);
                    if (transacted) {
                        session.commit();
                    }
                } catch (Exception e) {
                    if (transacted) {
                        try {
                            session.rollback();
                        } catch (JMSException | RuntimeException e2) {
                            throw new MessageListenerException(
                                "Problem rolling back transaction", e2);
                        }
                    }
                    throw new MessageListenerException(e.getMessage(), e);
                }
            });
            LOGGER.debug("registered {} listener {} for destination '{}'; " +
                    "transacted: {}, ack mode: {}",
                type.name().toLowerCase(), listener, destination, transacted, acknowledgeMode);
        } catch (JMSException | RuntimeException e) {
            throw new MessageListenerException(
                "Problem registering a MessageConsumer for " + destination, e);
        }
    }

    /**
     * Safely shuts down all open connections linked to the listener container.
     * To be executed when the bean is destroyed.
     *
     * @return true if all open connections are successfully closed
     */
    @PreDestroy
    public boolean shutdown() {
        final AtomicBoolean success = new AtomicBoolean(true);
        for (Connection connection : openConnections) {
            try {
                connection.stop();
            } catch (JMSException | RuntimeException e) {
                success.set(false);
                LOGGER.error("Failed to stop connection", e);
            }
        }
        return success.get();
    }

    @Override
    public String toString() {
        return "JMSListenerContainer{" +
            "openConnections=" + openConnections +
            ", connectionPool=" + connectionPool +
            ", threadPoolSize=" + threadPoolSize +
            ", maxThreadPoolSize=" + maxThreadPoolSize +
            ", type=" + type +
            '}';
    }

    private Destination lookupDestination(String destination,
                                          Session session) throws JMSException {
        return type == QUEUE ?
            session.createQueue(destination) :
            session.createTopic(destination);
    }
}
