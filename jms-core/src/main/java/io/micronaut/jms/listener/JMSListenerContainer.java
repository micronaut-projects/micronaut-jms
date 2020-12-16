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

import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.jms.Session.AUTO_ACKNOWLEDGE;

/***
 * A container for setting up and managing {@link MessageListener}s created by the
 *      {@link io.micronaut.jms.annotations.JMSListener} and
 *      {@link io.micronaut.jms.configuration.AbstractJMSListenerMethodProcessor}
 *      processing. There is support for programmatically instantiating listeners
 *      however this has not been fully incorporated into a coherent API yet and
 *      has not been fully tested. It is recommended to instead just use the
 *      {@link io.micronaut.jms.annotations.JMSListener} annotation on existing classes.
 *
 * @param <T> - the type of object that the incoming {@link javax.jms.Message} should be converted to before handling.
 *
 * @author elliottpope
 * @since 1.0
 */
public class JMSListenerContainer<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSListenerContainer.class);

    private final Set<Connection> openConnections = new HashSet<>();
    private final JMSConnectionPool connectionPool;
    private final int threadPoolSize;
    private final int maxThreadPoolSize;
    private final JMSDestinationType type;

    /***
     * Creates a {@link JMSListenerContainer} ready for listeners to be registered against.
     *
     * @param connectionPool - the {@link JMSConnectionPool} to pull {@link Connection}s from to create
     *                       {@link MessageListener}s
     * @param type - either {@link JMSDestinationType#QUEUE} or {@link JMSDestinationType#TOPIC}.
     */
    public JMSListenerContainer(JMSConnectionPool connectionPool,
                                JMSDestinationType type,
                                int threadPoolSize) {
        this(connectionPool, type, threadPoolSize, threadPoolSize);
    }

    public JMSListenerContainer(JMSConnectionPool connectionPool,
                                JMSDestinationType type,
                                int threadPoolSize,
                                int maxThreadPoolSize) {

        Assert.isTrue(maxThreadPoolSize >= threadPoolSize,
            "maxThreadPoolSize cannot be smaller than the threadPoolSize");

        this.connectionPool = connectionPool;
        this.type = type;
        this.threadPoolSize = threadPoolSize;
        this.maxThreadPoolSize = maxThreadPoolSize;
    }

    /***
     * Registers an {@link JMSListenerContainer} with default concurrency.
     *
     * NOTE: this method is not recommended and the annotation driven {@link io.micronaut.jms.annotations.JMSListener}
     *      is recommended
     *
     * @param destination
     * @param listener
     * @param clazz
     */
    public void registerListener(String destination,
                                 MessageHandler<T> listener,
                                 Class<T> clazz) {
        try {
            final Connection connection = connectionPool.createConnection();
            final Session session = connection.createSession(false, AUTO_ACKNOWLEDGE);
            openConnections.add(connection);
            final MessageConsumer consumer = session.createConsumer(
                session.createQueue(destination));
            consumer.setMessageListener(
                new MessageHandlerAdapter<>(
                    new ConcurrentMessageHandler<>(
                        listener,
                        new ThreadPoolExecutor(
                            threadPoolSize,
                            maxThreadPoolSize,
                            5L,
                            SECONDS,
                            new LinkedBlockingQueue<>(10),
                            Executors.defaultThreadFactory())),
                    clazz));
        } catch (JMSException e) {
            LOGGER.error("An error occurred while registering a MessageConsumer for " + destination, e);
        }
    }

    /***
     *
     * Internal method used by the {@link JMSListenerContainerFactory} for registering new listeners.
     *
     * NOTE: this method is used internally by the {@link io.micronaut.jms.configuration.AbstractJMSListenerMethodProcessor}
     *      and is not recommended for use. Instead the annotation driven {@link io.micronaut.jms.annotations.JMSListener}
     *      is preferred.
     *
     * @param destination
     * @param listener
     * @param clazz
     * @param transacted
     * @param acknowledgeMode
     */
    public void registerListener(String destination,
                                 MessageListener listener,
                                 Class<T> clazz, // TODO unused
                                 boolean transacted,
                                 int acknowledgeMode) {
        try {
            final Connection connection = connectionPool.createConnection();
            final Session session = connection.createSession(transacted, acknowledgeMode);
            openConnections.add(connection);
            final MessageConsumer consumer = session.createConsumer(
                lookupDestination(destination, session));
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
                        } catch (JMSException jmsException) {
                            LOGGER.error("Failed to rollback transaction due to an error.", jmsException);
                        }
                    }
                }
            });
        } catch (JMSException e) {
            LOGGER.error("An error occurred while registering a MessageConsumer for " + destination, e);
        }
    }

    /***
     *
     * Safely shuts down all open connections that are linked to the listener
     *      container. To be executed when the bean is destroyed.
     *
     * @return true if all open connections are successfully closed, false otherwise.
     */
    @PreDestroy
    public boolean shutdown() {
        final AtomicBoolean success = new AtomicBoolean(true);
        for (Connection connection : openConnections) {
            try {
                connection.stop();
            } catch (JMSException e) {
                success.set(false);
                LOGGER.error("Failed to stop connection die to an error", e);
            }
        }
        return success.get();
    }

    private Destination lookupDestination(String destination,
                                          Session session) throws JMSException {
        return type == QUEUE ?
            session.createQueue(destination) :
            session.createTopic(destination);
    }
}
