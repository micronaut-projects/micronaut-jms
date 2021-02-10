package io.micronaut.jms.listener;

import io.micronaut.jms.model.JMSDestinationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@Singleton
public class JMSListenerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSListenerRegistry.class);

    private final Set<JMSListener> listeners = Collections.synchronizedSet(Collections.emptySet());

    public void register(JMSListener listener, boolean autoStart) throws JMSException {
        // TODO: change to add if not present
        listener.addErrorHandlers(new LoggingJMSListenerErrorHandler());
        if (autoStart) {
            listener.start();
        }
        listeners.add(listener);
    }

    public void register(
            Connection connection,
            JMSDestinationType destinationType,
            String destination,
            final boolean transacted,
            final int acknowledgeMode,
            MessageListener delegate,
            ExecutorService executor,
            boolean autoStart) throws JMSException {
        connection.start();
        Session session = connection.createSession(transacted, acknowledgeMode);
        JMSListener listener = new JMSListener(session, delegate, destinationType, destination, executor);
        if (transacted) {
            listener.addSuccessHandlers(new TransactionalJMSListenerSuccessHandler());
            listener.addErrorHandlers(new TransactionalJMSListenerErrorHandler());
        }
        if (acknowledgeMode == Session.CLIENT_ACKNOWLEDGE) {
            listener.addSuccessHandlers(new AcknowledgingJMSListenerSuccessHandler());
        }
        listener.addErrorHandlers(new LoggingJMSListenerErrorHandler());
        this.register(listener, autoStart);

    }

    @PreDestroy
    public void shutdown() throws Exception {
        listeners.forEach(listener -> {
            try {
                listener.stop();
            } catch (JMSException e) {
                LOGGER.error("Failed to shutdown listener", e);
            }
        });
    }
}
