package io.micronaut.jms.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

public class TransactionalJMSListenerErrorHandler implements JMSListenerErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionalJMSListenerErrorHandler.class);

    @Override
    public void handle(Session session, Message message, Throwable ex) {
        LOGGER.debug("Attempting to rollback transaction on session {} for message {} due to {}", session, message, ex);
        try {
            session.rollback();
            LOGGER.debug("Successfully rolled back transaction on session {} for message {}", session, message);
        } catch (JMSException e) {
            LOGGER.error("Failed to rollback transaction on session", e);
        }
    }
}
