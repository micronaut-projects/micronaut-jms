package io.micronaut.jms.listener;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

public class TransactionalJMSListenerSuccessHandler implements JMSListenerSuccessHandler {
    @Override
    public void handle(Session session, Message message) throws JMSException {
        session.commit();
    }
}
