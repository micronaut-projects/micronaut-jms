package io.micronaut.jms.listener;

import io.micronaut.messaging.exceptions.MessageAcknowledgementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

public class AcknowledgingJMSListenerSuccessHandler implements JMSListenerSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(AcknowledgingJMSListenerSuccessHandler.class);

    @Override
    public void handle(Session session, Message message) throws JMSException {
        try {
            message.acknowledge();
        } catch (JMSException e) {
            logger.error("Failed to acknowledge receipt of message with the broker. " +
                    "This message may be falsely retried.", e);
            throw new MessageAcknowledgementException(e.getMessage(), e);
        }
    }
}
