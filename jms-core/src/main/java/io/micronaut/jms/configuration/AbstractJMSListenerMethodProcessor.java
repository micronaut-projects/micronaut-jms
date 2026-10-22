/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.jms.configuration;

import io.micronaut.context.BeanContext;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.type.Executable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.bind.JMSArgumentBinderRegistry;
import io.micronaut.jms.listener.JMSListenerErrorHandler;
import io.micronaut.jms.listener.JMSListenerRegistry;
import io.micronaut.jms.listener.JMSTopicSubscription;
import io.micronaut.jms.listener.JMSListenerSuccessHandler;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.util.Assert;
import io.micronaut.messaging.annotation.MessageBody;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract {@link ExecutableMethodProcessor} for annotations related to
 * {@link JMSListener}. Registers a {@link io.micronaut.jms.listener.JMSListener}
 * if the method annotated with {@code <T>} is part of a bean annotated with {@link JMSListener}.
 *
 * @param <T> the destination type annotation
 * @author Elliott Pope, sbodvanski
 * @since 1.0.0
 */
public abstract class AbstractJMSListenerMethodProcessor<T extends Annotation>
    implements ExecutableMethodProcessor<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final BeanContext beanContext;

    private final JMSArgumentBinderRegistry jmsArgumentBinderRegistry;
    private final Class<T> clazz;

    protected AbstractJMSListenerMethodProcessor(BeanContext beanContext,
                                                 JMSArgumentBinderRegistry registry,
                                                 Class<T> clazz) {
        this.beanContext = beanContext;
        this.jmsArgumentBinderRegistry = registry;
        this.clazz = clazz;
    }

    @Override
    public <B> void process(BeanDefinition<B> beanDefinition,
                        ExecutableMethod<B, ?> method) {

        AnnotationValue<JMSListener> listenerAnnotation = beanDefinition.getAnnotation(JMSListener.class);
        if (listenerAnnotation == null) {
            return;
        }

        String connectionFactoryName = listenerAnnotation.getRequiredValue(String.class);

        AnnotationValue<T> destinationAnnotation = method.getAnnotation(clazz);
        Assert.notNull(destinationAnnotation, () -> "Annotation not found on method " +
            method.getName() + ". Expecting annotation of type " + clazz.getName());

        registerListener(method, connectionFactoryName, beanDefinition,
            destinationAnnotation, getDestinationType());
    }

    protected abstract ExecutorService getExecutorService(AnnotationValue<T> value);

    protected abstract JMSDestinationType getDestinationType();

    private void validateArguments(ExecutableMethod<?, ?> method) {
        Stream.of(method.getArguments())
            .filter(arg ->
                arg.isDeclaredAnnotationPresent(MessageBody.class) ||
                arg.isDeclaredAnnotationPresent(io.micronaut.jms.annotations.Message.class))
            .findAny()
            .orElseThrow(() -> new IllegalStateException(
                "Methods annotated with @" + clazz.getSimpleName() +
                    " must have exactly one argument annotated with @Body" +
                    " or @Message"));
    }

    private MessageListener generateAndBindListener(Object bean,
                                                    Executable<?, ?> method) {

        return message -> {
            DefaultExecutableBinder<Message> binder = new DefaultExecutableBinder<>();
            BoundExecutable boundExecutable = binder.bind(method, jmsArgumentBinderRegistry, message);
            boundExecutable.invoke(bean);
        };
    }

    private static @Nullable JMSTopicSubscription topicSubscription(AnnotationValue<?> destinationAnnotation, String destination) {
        String name = destinationAnnotation.stringValue("subscription").filter(s -> !s.isEmpty()).orElse(null);
        boolean durable = destinationAnnotation.isTrue("durable");
        boolean shared = destinationAnnotation.isTrue("shared");
        String clientId = destinationAnnotation.stringValue("clientId").filter(s -> !s.isEmpty()).orElse(null);
        if (name == null) {
            if (durable || shared || clientId != null) {
                throw new IllegalStateException("A subscription name is required for the durable, shared or client ID options of topic " + destination);
            }
            return null;
        }
        return new JMSTopicSubscription(name, durable, shared, clientId);
    }

    private void registerListener(ExecutableMethod<?, ?> method,
                                  String connectionFactoryName,
                                  BeanDefinition<?> beanDefinition,
                                  AnnotationValue<T> destinationAnnotation,
                                  JMSDestinationType type) {

        validateArguments(method);

        final String destination = destinationAnnotation.getRequiredValue(String.class);
        final int acknowledgeMode = destinationAnnotation.getRequiredValue("acknowledgeMode", Integer.class);
        final boolean transacted = destinationAnnotation.getRequiredValue("transacted", Boolean.class);
        final Optional<String> messageSelector = destinationAnnotation.get("messageSelector", String.class);
        final JMSTopicSubscription subscription = topicSubscription(destinationAnnotation, destination);

        final JMSListenerRegistry registry = beanContext
                .findBean(JMSListenerRegistry.class)
                .orElseThrow(() -> new IllegalStateException("No JMSListenerRegistry configured"));

        final JMSConnectionPool connectionPool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactoryName));
        final Object bean = beanContext.getBean(beanDefinition.getBeanType());
        final ExecutorService executor = getExecutorService(destinationAnnotation);

        MessageListener listener = generateAndBindListener(bean, method);

        Set<JMSListenerErrorHandler> errorHandlers = Stream.concat(
                        Arrays.stream(destinationAnnotation.classValues("errorHandlers")),
                        Arrays.stream(beanDefinition.classValues(JMSListener.class, "errorHandlers")))
                .filter(JMSListenerErrorHandler.class::isAssignableFrom)
                .map(handlerClass -> (Class<? extends JMSListenerErrorHandler>) handlerClass)
                .map(beanContext::findBean)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        Set<JMSListenerSuccessHandler> successHandlers = Stream.concat(
                        Arrays.stream(destinationAnnotation.classValues("successHandlers")),
                        Arrays.stream(beanDefinition.classValues(JMSListener.class, "successHandlers")))
                .filter(JMSListenerSuccessHandler.class::isAssignableFrom)
                .map(handlerClass -> (Class<? extends JMSListenerSuccessHandler>) handlerClass)
                .map(beanContext::findBean)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        try {
            Connection connection;
            if (subscription != null && subscription.requiresDedicatedConnection()) {
                // the client ID must be set before the connection is used, so it can't come from the pool
                connection = connectionPool.getConnectionFactory().createConnection();
                connection.setClientID(subscription.effectiveClientId());
            } else {
                connection = connectionPool.createConnection();
            }
            io.micronaut.jms.listener.JMSListener registeredListener = registry.register(
                    connection, type, destination, transacted, acknowledgeMode, listener, executor, true, messageSelector, subscription);
            registeredListener.addSuccessHandlers(successHandlers);
            registeredListener.addErrorHandlers(errorHandlers);
        } catch (JMSException e) {
            logger.error("Failed to register listener for destination " + destination, e);
        }
    }
}
