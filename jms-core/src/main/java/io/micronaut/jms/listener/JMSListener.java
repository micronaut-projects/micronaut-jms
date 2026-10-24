/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.order.OrderUtil;
import io.micronaut.jms.model.JMSDestinationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;

/**
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
 * {@link JMSListenerErrorHandler}s are called sequentially.
 *
 * Note: To handle the special cases (e.g.the negative acknowledger feature in the AWS SQS base implementation), an error handler could throw an error to expose it to the base implementation.
 * However, it must be ensured that the error handler executes after all other error handlers.
 *
 * @author Elliott Pope
 * @since 2.1.1
 */
public class JMSListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSListener.class);

    private final Session session;
    private final MessageListener delegate;
    private MessageConsumer consumer;
    private final JMSDestinationType destinationType;
    private final String destination;
    private final ExecutorService executor;
    private final List<JMSListenerSuccessHandler> successHandlers = new ArrayList<>();
    private final List<JMSListenerErrorHandler> errorHandlers = new ArrayList<>();
    private final Optional<String> messageSelector;
    private final @Nullable JMSTopicSubscription subscription;

    /**
     * Creates a {@link JMSListener} instance. This instance will not begin listening for messages until
     *  {@link JMSListener#start()} is called. The provided session's parent {@link jakarta.jms.Connection}
     *  must be started ({@link jakarta.jms.Connection#start()}) for the message listener to receive messages.
     * @param session - the {@link Session} for the messages to be consumed on
     * @param delegate - the listener logic to be invoked. All concurrency, success, and error handling is provided.
     *                 This {@link MessageListener} should extract the necessary data from the {@link jakarta.jms.Message}
     *                 and perform application specific logic.
     * @param destinationType - the {@link JMSDestinationType} of the target destination
     * @param destination - the name of the target destination
     * @param executor - the {@link ExecutorService} to perform the message handling logic on. The message handling, including
*                 success and error handling, is performed on threads managed by this executor.
     * @param messageSelector the message selector for the listener
     */
    public JMSListener(Session session, MessageListener delegate, JMSDestinationType destinationType, String destination, ExecutorService executor, Optional<String> messageSelector) {
        this(session, delegate, destinationType, destination, executor, messageSelector, null);
    }

    /**
     * Creates a {@link JMSListener} instance, optionally with a named topic subscription. This instance will
     * not begin listening for messages until {@link JMSListener#start()} is called.
     *
     * @param session - the {@link Session} for the messages to be consumed on
     * @param delegate - the listener logic to be invoked
     * @param destinationType - the {@link JMSDestinationType} of the target destination
     * @param destination - the name of the target destination
     * @param executor - the {@link ExecutorService} to perform the message handling logic on, or {@code null}
     * @param messageSelector the message selector for the listener
     * @param subscription the named subscription to consume from, only for topics, or {@code null}
     * @since 5.2.0
     */
    public JMSListener(Session session, MessageListener delegate, JMSDestinationType destinationType, String destination, ExecutorService executor, Optional<String> messageSelector, @Nullable JMSTopicSubscription subscription) {
        if (subscription != null && destinationType == QUEUE) {
            throw new IllegalArgumentException("A subscription can only be used with a topic, not with queue " + destination);
        }
        this.session = session;
        this.delegate = delegate;
        this.destinationType = destinationType;
        this.destination = destination;
        this.executor = executor;
        this.messageSelector = messageSelector;
        this.subscription = subscription;
    }

    /**
     * @param handlers - add the given handlers to the success handlers for this listener. The handlers will be added
     *                 such that the {@link JMSListenerSuccessHandler#getOrder()} is decreasing along the list.
     */
    public void addSuccessHandlers(JMSListenerSuccessHandler... handlers) {
        this.addSuccessHandlers(Arrays.asList(handlers));
    }

    /**
     * @param handlers - add the given handlers to the success handlers for this listener. The handlers will be added
     *                 such that the {@link JMSListenerSuccessHandler#getOrder()} is decreasing along the list.
     */
    public void addSuccessHandlers(Collection<? extends JMSListenerSuccessHandler> handlers) {
        handlers.forEach(handler -> orderedInsert(this.successHandlers, handler));
    }

    /**
     * @param handlers - add the given handlers to the error handlers for this listener. The handlers will be added
     *                 such that the {@link JMSListenerErrorHandler#getOrder()} is decreasing along the list.
     */
    public void addErrorHandlers(JMSListenerErrorHandler... handlers) {
        this.addErrorHandlers(Arrays.asList(handlers));
    }

    /**
     * @param handlers - add the given handlers to the error handlers for this listener. The handlers will be added
     *                 such that the {@link JMSListenerErrorHandler#getOrder()} is decreasing along the list.
     */
    public void addErrorHandlers(Collection<? extends JMSListenerErrorHandler> handlers) {
        handlers.forEach(handler -> orderedInsert(this.errorHandlers, handler));
    }

    /**
     * Configures the listener to begin listening for messages and processing them.
     *
     * @throws JMSException - if any JMS related exception occurs while configuring the listener.
     */
    @SuppressWarnings("java:S2095") // The consumer is closed in the stop method
    public void start() throws JMSException {
        MessageConsumer messageConsumer;
        if (subscription != null) {
            messageConsumer = createSubscriptionConsumer(subscription);
        } else if (messageSelector.isPresent() && !messageSelector.get().isEmpty()) {
            messageConsumer = session.createConsumer(lookupDestination(destinationType, destination, session), messageSelector.get());
        } else {
            messageConsumer = session.createConsumer(lookupDestination(destinationType, destination, session));
        }

        if (executor == null) {
            messageConsumer.setMessageListener(this::handleMessage);
        } else {
            messageConsumer.setMessageListener(msg -> executor.submit(
                () -> handleMessage(msg)));
        }

        this.consumer = messageConsumer;
    }

    private void handleMessage(Message msg) {
        try {
            if (delegate instanceof SessionAwareMessageListener sessionAwareListener) {
                sessionAwareListener.onMessage(msg, session);
            } else {
                delegate.onMessage(msg);
            }
            Throwable ex = new Throwable();
            successHandlers.forEach(handler -> {
                try {
                    handler.handle(session, msg);
                } catch (JMSException e) {
                    LOGGER.error("Failed to handle successful message receive: " + e.getMessage(), e);
                    ex.addSuppressed(e);
                }
            });
            if (ex.getSuppressed().length > 0) {
                errorHandlers.forEach(handler -> handler.handle(session, msg, ex));
            }
        } catch (Exception e) {
            errorHandlers.forEach(handler -> handler.handle(session, msg, e));
        }
    }

    /**
     * Stops the listener from consuming messages and attempts to clean up any resources used.
     *
     * @throws JMSException - if any error occurs while shutting down the listener.
     */
    public void stop() throws JMSException {
        consumer.close();
        session.close();
    }

    private MessageConsumer createSubscriptionConsumer(JMSTopicSubscription subscription) throws JMSException {
        Topic topic = session.createTopic(destination);
        String selector = messageSelector.filter(s -> !s.isEmpty()).orElse(null);
        if (subscription.shared()) {
            try {
                return subscription.durable()
                    ? session.createSharedDurableConsumer(topic, subscription.name(), selector)
                    : session.createSharedConsumer(topic, subscription.name(), selector);
            } catch (UnsupportedOperationException e) {
                // for example ActiveMQ Classic, which implements JMS 1.1 subscriptions only
                JMSException unsupported = new JMSException("The JMS provider does not support shared subscriptions (subscription '"
                    + subscription.name() + "' on topic " + destination + ")");
                unsupported.initCause(e);
                throw unsupported;
            }
        }
        if (subscription.durable()) {
            return session.createDurableSubscriber(topic, subscription.name(), selector, false);
        }
        // a named subscription that is neither durable nor shared is an ordinary consumer
        return selector == null ? session.createConsumer(topic) : session.createConsumer(topic, selector);
    }

    private static Destination lookupDestination(JMSDestinationType destinationType,
                                                 String destination,
                                                 Session session) throws JMSException {
        return destinationType == QUEUE ?
                session.createQueue(destination) :
                session.createTopic(destination);
    }

    private static <T> void orderedInsert(List<T> existingList, T element) {
        existingList.add(element);
        existingList.sort(OrderUtil.REVERSE_COMPARATOR);
    }

}

