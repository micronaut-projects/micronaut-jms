package io.micronaut.jms.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

public class LoggingJMSListenerErrorHandler implements JMSListenerErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingJMSListenerErrorHandler.class);
    
    @Override
    public void handle(Session session, Message message, Throwable ex) {
        LOGGER.error("Failed to handle message receive", ex);
    }
}
