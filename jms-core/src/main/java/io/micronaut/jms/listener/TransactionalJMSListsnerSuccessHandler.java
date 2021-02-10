package io.micronaut.jms.listener;

import javax.jms.Message;
import javax.jms.Session;

public class TransactionalJMSListsnerSuccessHandler implements JMSListenerSuccessHandler {
    @Override
    public void handle(Session session, Message message) {
        session.commit();
    }
}
