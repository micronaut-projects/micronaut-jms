/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/***
 * Registry for all {@link JMSListener}s managed by Micronaut JMS. Listeners can be dynamically registered
 *  using the {@link JMSListenerRegistry#register(Connection, JMSDestinationType, String, boolean, int, MessageListener, ExecutorService, boolean)}
 *  method. When the application context closes, all open connections, listeners, and sessions, are closed.
 *
 * @author Elliott Pope
 * @since 1.0.0.M2
 */
@Singleton
public class JMSListenerRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSListenerRegistry.class);

    private final Set<JMSListener> listeners = Collections.synchronizedSet(new HashSet<>());

    /***
     * Registers a new listener to be managed by Micronaut JMS.
     *
     * @param listener - the listener to be registered
     * @param autoStart - whether the listener should be automatically started when registered
     * @throws JMSException - if the listener fails to start
     */
    public void register(JMSListener listener, boolean autoStart) throws JMSException {
        // TODO: change to add if not present
        listener.addErrorHandlers(new LoggingJMSListenerErrorHandler());
        if (autoStart) {
            listener.start();
        }
        listeners.add(listener);
    }

    /***
     * Creates and registers a new listener to be managed by Micronaut JMS.
     *
     * @param connection - the {@link Connection} the listener will be linked to
     * @param destinationType - the {@link JMSDestinationType} of the target destination
     * @param destination - the name of the target destination
     * @param transacted - whether the listener should commit the transaction once the message is received
     * @param acknowledgeMode - whether the message receipt should be acknowledged
     * @param delegate - the underlying
     * @param executor
     * @param autoStart
     * @throws JMSException
     * @return the listener that has been registered.
     */
    public JMSListener register(
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
        return listener;
    }

    /***
     * Shuts down all registered {@link JMSListener}s. If a listener fails to shut down then it is logged and skipped.
     */
    @PreDestroy
    public void shutdown() {
        listeners.forEach(listener -> {
            try {
                listener.stop();
            } catch (JMSException e) {
                LOGGER.error("Failed to shutdown listener", e);
            }
        });
        listeners.clear();
    }
}
