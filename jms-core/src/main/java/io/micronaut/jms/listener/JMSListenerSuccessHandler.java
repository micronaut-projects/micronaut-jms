package io.micronaut.jms.listener;

import javax.jms.Message;
import javax.jms.Session;

@FunctionalInterface
public interface JMSListenerSuccessHandler {
    void handle(Session session, Message message);
}
