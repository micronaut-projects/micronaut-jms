package io.micronaut.jms.listener;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class JMSListener {
    private final Session session;
    private final MessageListener delegate;
    private final Boolean transacted;
    private final Integer acknowledgeMode;
    private final ExecutorService executor;
    private final Set<JMSListenerSuccessHandler> successHandlers= Collections.emptySet();
    private final Set<JMSListenerErrorHandler> errorHandlers= Collections.emptySet();

    public JMSListener(Session session, MessageListener delegate, Boolean transacted, Integer acknowledgeMode, ExecutorService executor) {
        this.session = session;
        this.delegate = delegate;
        this.transacted = transacted;
        this.acknowledgeMode = acknowledgeMode;
        this.executor = executor;
    }

    public void start() throws JMSException {
        session.setMessageListener((msg) -> {
            try {
                delegate.onMessage(msg);
                // success handlers
            } catch (Throwable e) {
                // error handlers
            }
        });
    }

    public void stop() throws JMSException {
        session.close();
    }

}

@FunctionalInterface
interface JMSListenerSuccessHandler {
    void handle(Session session, Message message);
}

@FunctionalInterface
interface JMSListenerErrorHandler {
    void handle(Session session, Message message, Throwable ex);
}
