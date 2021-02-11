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

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;

/***
 * Sets up and manages {@link MessageListener}s created by the {@link io.micronaut.jms.annotations.JMSListener} and
 *  {@link io.micronaut.jms.configuration.AbstractJMSListenerMethodProcessor} processing.
 * Additional handlers can be added to inject custom success and error handling cases (see {@link TransactionalJMSListenerErrorHandler},
 *  {@link TransactionalJMSListenerSuccessHandler}, {@link AcknowledgingJMSListenerSuccessHandler}, and {@link LoggingJMSListenerErrorHandler})
 *  using the {@link JMSListener#addSuccessHandlers(JMSListenerSuccessHandler...)} and {@link JMSListener#addErrorHandlers(JMSListenerErrorHandler...)}
 *  methods.
 * Once a message is successfully processed by the {@link JMSListener#delegate} then all the {@link JMSListenerSuccessHandler}s
 *  are called sequentially. If a handler throws an error then it is caught and once all handlers have completed, those errors
 *  are rethrown
 * If any error is thrown during message handling (either by the listener itself, or by a success handler), then all the
 *  {@link JMSListenerErrorHandler}s are called sequentially. Error handlers cannot themselves throw errors.
 *
 * @author Elliott Pope
 * @since 1.0.0.M2
 */
class JMSListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSListener.class);

    private final Session session;
    private final MessageListener delegate;
    private MessageConsumer consumer;
    private final JMSDestinationType destinationType;
    private final String destination;
    private final ExecutorService executor;
    private final Set<JMSListenerSuccessHandler> successHandlers = new HashSet<>();
    private final Set<JMSListenerErrorHandler> errorHandlers = new HashSet<>();

    /***
     * Creates a {@link JMSListener} instance. This instance will not begin listening for messages until
     *  {@link JMSListener#start()} is called. The provided {@param session}'s parent {@link javax.jms.Connection}
     *  must be started ({@link javax.jms.Connection#start()}) for the message listener to receive messages.
     *
     * @param session - the {@link Session} for the messages to be consumed on
     * @param delegate - the listener logic to be invoked. All concurrency, success, and error handling is provided.
     *                 This {@link MessageListener} should extract the necessary data from the {@link javax.jms.Message}
     *                 and perform application specific logic.
     * @param destinationType - the {@link JMSDestinationType} of the target destination
     * @param destination - the name of the target destination
     * @param executor - the {@link ExecutorService} to perform the message handling logic on. The message handling, including
     *                 success and error handling, is performed on threads managed by this {@param executor}.
     */
    public JMSListener(Session session, MessageListener delegate, JMSDestinationType destinationType, String destination, ExecutorService executor) {
        this.session = session;
        this.delegate = delegate;
        this.destinationType = destinationType;
        this.destination = destination;
        this.executor = executor;
    }

    public void addSuccessHandlers(JMSListenerSuccessHandler... handlers) {
        this.addSuccessHandlers(Arrays.asList(handlers));
    }

    public void addSuccessHandlers(Collection<JMSListenerSuccessHandler> handlers) {
        successHandlers.addAll(handlers);
    }

    public void addErrorHandlers(JMSListenerErrorHandler... handlers) {
        this.addErrorHandlers(Arrays.asList(handlers));
    }

    public void addErrorHandlers(Collection<JMSListenerErrorHandler> handlers) {
        errorHandlers.addAll(handlers);
    }

    public void start() throws JMSException {
        final MessageConsumer consumer = session.createConsumer(lookupDestination(destinationType, destination, session));
        consumer.setMessageListener((msg) -> executor.submit(() -> {
            try {
                delegate.onMessage(msg);
                Throwable ex = new Throwable();
                successHandlers.forEach(handler -> {
                    try {
                        handler.handle(session, msg);
                    } catch (JMSException e) {
                        LOGGER.error("Failed to handle successful message receive", e);
                        ex.addSuppressed(e);
                    }
                });
                if (ex.getSuppressed().length > 0) {
                    throw ex;
                }
            } catch (Throwable e) {
                errorHandlers.forEach(handler -> handler.handle(session, msg, e));
            }
        }));
        this.consumer = consumer;
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

