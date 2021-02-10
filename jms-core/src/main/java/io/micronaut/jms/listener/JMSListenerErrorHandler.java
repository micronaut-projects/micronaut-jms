package io.micronaut.jms.listener;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

@FunctionalInterface
public interface JMSListenerErrorHandler {
    void handle(Session session, Message message, Throwable ex);
}
