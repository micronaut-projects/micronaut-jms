package io.micronaut.jms.listener;

import io.micronaut.jms.model.JMSDestinationType;

import javax.jms.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;

public class JMSListener {
    private final Session session;
    private final MessageListener delegate;
    private MessageConsumer consumer;
    private final JMSDestinationType destinationType;
    private final String destination;
    private final ExecutorService executor;
    private final Set<JMSListenerSuccessHandler> successHandlers = Collections.emptySet();
    private final Set<JMSListenerErrorHandler> errorHandlers = Collections.emptySet();

    public JMSListener(Session session, MessageListener delegate, JMSDestinationType destinationType, String destination, ExecutorService executor) {
        this.session = session;
        this.delegate = delegate;
        this.destinationType = destinationType;
        this.destination = destination;
        this.executor = executor;
    }

    public void addJMSListenerSuccessHandlers(JMSListenerSuccessHandler... handlers) {
        this.addJMSListenerSuccessHandlers(Arrays.asList(handlers));
    }

    public void addJMSListenerSuccessHandlers(Collection<JMSListenerSuccessHandler> handlers) {
        successHandlers.addAll(handlers);
    }

    public void start() throws JMSException {
        final MessageConsumer consumer = session.createConsumer(lookupDestination(destinationType, destination, session));
        consumer.setMessageListener((msg) -> executor.submit(() -> {
            try {
                delegate.onMessage(msg);
                successHandlers.forEach(handler -> handler.handle(session, msg));
            } catch (Throwable e) {
                errorHandlers.forEach(handler -> handler.handle(session, msg, e));
            }
        }));
    }

    public void stop() throws JMSException {
        consumer.close();
        session.close();
    }

    private static Destination lookupDestination(JMSDestinationType destinationType,
                                                 String destination,
                                                 Session session) throws JMSException {
        return destinationType == QUEUE ?
                session.createQueue(destination) :
                session.createTopic(destination);
    }

}

