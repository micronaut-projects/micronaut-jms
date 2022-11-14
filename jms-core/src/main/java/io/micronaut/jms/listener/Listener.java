package io.micronaut.jms.listener;

import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.messaging.exceptions.MessageListenerException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;

public class Listener<T extends Broker> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Listener.class);
    private final Connection connection;
    private final Session session;
    private final MessageConsumer consumer;
    private final T queue;

    private Listener(Connection connection, Session session, MessageConsumer consumer, T queue) {
        this.connection = connection;
        this.session = session;
        this.consumer = consumer;
        this.queue = queue;
    }

    public static <T extends Broker> Listener register(JMSConnectionPool pool, T queue, MessageListener listener) {
        try {
            final Connection connection = pool.createConnection();
            final Session session = connection.createSession(queue.isTransacted(), queue.getAcknowledgeMode());
            MessageConsumer consumer = getConsumer(queue, session);

            consumer.setMessageListener((message) -> {
                try {
                    listener.onMessage(message);
                    if (queue.isTransacted()) {
                        session.commit();
                    }
                } catch (Exception e) {
                    if (queue.isTransacted()) {
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
                    "queue", listener, queue.getName(), queue.isTransacted(), queue.getAcknowledgeMode());
            return new Listener<>(connection, session, consumer, queue);
        } catch (JMSException | RuntimeException e) {
            throw new MessageListenerException(
                    "Problem registering a MessageConsumer for " + queue.getName(), e);
        }
    }

    @PreDestroy
    public boolean shutdown() {
        try {
            this.consumer.close();
            this.session.close();
            this.connection.close();
            return true;
        } catch (JMSException e) {
            throw new MessageListenerException("Failed to shutdown listener for " + queue.getName(), e);
        }
    }

    private static <T extends Broker> MessageConsumer getConsumer(T broker, Session session) throws JMSException {
        if (Topic.class.equals(broker.getClass())) {
            Topic topic = (Topic)broker;
            javax.jms.Topic destination = (javax.jms.Topic) topic.getDestination(session);
            if (topic.isShared() && topic.isDurable()) {
                return broker.getMessageSelector() == null ?
                        session.createSharedDurableConsumer(destination, "name") :
                        session.createSharedDurableConsumer(destination, "name", broker.getMessageSelector());
            } else if (topic.isShared()) {
                return broker.getMessageSelector() == null ?
                        session.createSharedConsumer(destination, "name") :
                        session.createSharedConsumer(destination, "name", broker.getMessageSelector());

            } else if (topic.isDurable()) {
                return broker.getMessageSelector() == null ?
                        session.createDurableConsumer(destination, "name") :
                        session.createDurableConsumer(destination, "name", broker.getMessageSelector(), true);
            }
        }
        return broker.getMessageSelector() == null ?
            session.createConsumer(broker.getDestination(session)) :
            session.createConsumer(broker.getDestination(session), broker.getMessageSelector());
    }
}
