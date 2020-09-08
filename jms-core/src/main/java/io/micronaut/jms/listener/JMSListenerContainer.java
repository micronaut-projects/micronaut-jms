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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JMSListenerContainer<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSListenerContainer.class);

    private final JMSConnectionPool connectionPool;

    private final Set<Connection> openConnections = new HashSet<>();
    private boolean isRunning = true;
    private int threadPoolSize;
    private int maxThreadPoolSize;

    private final JMSDestinationType type;

    public JMSListenerContainer(
            JMSConnectionPool connectionPool,
            JMSDestinationType type) {
        this.connectionPool = connectionPool;
        this.type = type;
    }

    /***
     *
     * Sets the minimum core thread pool size for the listener container.
     *
     * @param threadPoolSize
     */
    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
        this.maxThreadPoolSize = Math.max(maxThreadPoolSize, threadPoolSize);
    }

    /***
     * Sets the maximum number of threads that can handle incoming requests.
     *
     * @param maxThreadPoolSize
     */
    public void setMaxThreadPoolSize(int maxThreadPoolSize) {
        if (maxThreadPoolSize < this.threadPoolSize) {
            throw new IllegalArgumentException("maxThreadPoolSize cannot be smaller than the threadPoolSize");
        }
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
    public void registerListener(String destination, MessageHandler<T> listener, Class<T> clazz) {
        try  {
            final Connection connection = connectionPool.createConnection();
            final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            openConnections.add(connection);
            final MessageConsumer consumer = session.createConsumer(session.createQueue(destination));
            consumer.setMessageListener(
                    new MessageHandlerAdapter<>(
                            new ConcurrentMessageHandler<>(
                                    listener,
                                    new ThreadPoolExecutor(
                                            threadPoolSize,
                                            maxThreadPoolSize,
                                            5L,
                                            TimeUnit.SECONDS,
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
     * @param acknowledgment
     */
    public void registerListener(String destination, MessageListener listener, Class<T> clazz, boolean transacted, int acknowledgment) {
        try  {
            final Connection connection = connectionPool.createConnection();
            final Session session = connection.createSession(transacted, acknowledgment);
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
        openConnections.forEach(connection -> {
            try {
                connection.stop();
            } catch (JMSException e) {
                success.set(false);
                LOGGER.error("Failed to stop connection die to an error", e);
            }
        });
        this.isRunning = success.get();
        return isRunning;
    }

    private Destination lookupDestination(
            String destination,
            Session session) throws JMSException {
        return type == JMSDestinationType.QUEUE ?
                session.createQueue(destination) :
                session.createTopic(destination);
    }
}
