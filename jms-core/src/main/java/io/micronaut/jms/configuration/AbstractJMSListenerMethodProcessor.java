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
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.Argument;
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
import io.micronaut.jms.listener.SessionAwareMessageListener;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.util.Assert;
import io.micronaut.messaging.annotation.MessageBody;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
        // a parameter typed as the JMS session, or a provider subtype, receives the session that consumed the message
        Argument<?>[] sessionArguments = Arrays.stream(method.getArguments())
            .filter(argument -> Session.class.isAssignableFrom(argument.getType())
                && !argument.getAnnotationMetadata().hasStereotype(Bindable.class))
            .toArray(Argument[]::new);
        if (sessionArguments.length == 0) {
            return message -> {
                DefaultExecutableBinder<Message> binder = new DefaultExecutableBinder<>();
                BoundExecutable boundExecutable = binder.bind(method, jmsArgumentBinderRegistry, message);
                boundExecutable.invoke(bean);
            };
        }
        return new SessionAwareMessageListener() {
            @Override
            public void onMessage(Message message, Session session) {
                Map<Argument<?>, Object> preBound = new HashMap<>(sessionArguments.length);
                for (Argument<?> argument : sessionArguments) {
                    preBound.put(argument, session);
                }
                DefaultExecutableBinder<Message> binder = new DefaultExecutableBinder<>(preBound);
                BoundExecutable boundExecutable = binder.bind(method, jmsArgumentBinderRegistry, message);
                boundExecutable.invoke(bean);
            }

            @Override
            public void checkSession(Session session) {
                for (Argument<?> argument : sessionArguments) {
                    if (!argument.getType().isInstance(session)) {
                        throw new IllegalStateException("The argument '" + argument.getName() + "' of type " + argument.getTypeName()
                            + " cannot be bound to the session "
                            + session.getClass().getName());
                    }
                }
            }
        };
    }

    private static @Nullable JMSTopicSubscription topicSubscription(AnnotationValue<?> destinationAnnotation, String destination) {
        return JMSTopicSubscription.of(destination,
            destinationAnnotation.stringValue("subscription").orElse(null),
            destinationAnnotation.isTrue("durable"),
            destinationAnnotation.isTrue("shared"),
            destinationAnnotation.stringValue("clientId").orElse(null));
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
            io.micronaut.jms.listener.JMSListener registeredListener = registry.register(
                    connectionPool, type, destination, transacted, acknowledgeMode, listener, executor, true, messageSelector, subscription);
            registeredListener.addSuccessHandlers(successHandlers);
            registeredListener.addErrorHandlers(errorHandlers);
        } catch (JMSException e) {
            logger.error("Failed to register listener for destination " + destination, e);
        }
    }
}
