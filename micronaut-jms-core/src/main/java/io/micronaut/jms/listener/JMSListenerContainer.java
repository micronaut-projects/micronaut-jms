package io.micronaut.jms.listener;

import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micronaut.jms.model.JMSDestinationType;

import javax.annotation.PreDestroy;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JMSListenerContainer<T> {
    private ConnectionFactory connectionFactory;

    private final Set<Connection> openConnections = new HashSet<>();
    private boolean isRunning = true;
    private int threadPoolSize;
    private int maxThreadPoolSize;

    private final JMSDestinationType type;

    public JMSListenerContainer(
            ConnectionFactory connectionFactory,
            JMSDestinationType type) {
        this.connectionFactory = connectionFactory;
        this.type = type;
    }

    /***
     *
     *
     * @return the {@link ConnectionFactory} for this listener.
     */
    public ConnectionFactory getConnectionFactory() {
        return Optional.ofNullable(connectionFactory).orElseThrow(
                () -> new IllegalStateException("No ConnectionFactory configured"));
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
            final Connection connection = getConnectionFactory().createConnection();
            final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            connection.start();
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
                                            new NamedThreadFactory(destination + "-pool-1-thread"))),
                            clazz));
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    /***
     *
     * Internal method used by the {@link JMSListenerContainerFactory} for registering new listeners.
     *
     * NOTE: this method is used internally by the {@link io.micronaut.jms.configuration.JMSListenerMethodProcessor}
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
            final Connection connection = getConnectionFactory().createConnection();
            final Session session = connection.createSession(transacted, acknowledgment);
            connection.start();
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
                            jmsException.printStackTrace();
                        }
                    }
                }
            });
        } catch (JMSException e) {
            e.printStackTrace();
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
                e.printStackTrace();
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
