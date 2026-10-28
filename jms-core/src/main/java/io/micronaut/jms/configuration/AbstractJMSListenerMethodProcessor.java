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
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.bind.DefaultBodyArgumentBinder;
import io.micronaut.jms.bind.JMSArgumentBinderRegistry;
import io.micronaut.jms.listener.JMSListenerErrorHandler;
import io.micronaut.jms.listener.JMSListenerRegistry;
import io.micronaut.jms.listener.JMSTopicSubscription;
import io.micronaut.jms.listener.JMSListenerSuccessHandler;
import io.micronaut.jms.listener.SessionAwareMessageListener;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.Deserializer;
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
import java.util.List;
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

    private ArgumentBinderRegistry<Message> binderRegistry(AnnotationValue<T> destinationAnnotation) {
        Optional<String> deserializerName = destinationAnnotation.stringValue("deserializer").filter(name -> !name.isEmpty());
        if (deserializerName.isEmpty()) {
            return jmsArgumentBinderRegistry;
        }
        Deserializer deserializer = beanContext.getBean(Deserializer.class, Qualifiers.byName(deserializerName.get()));
        ArgumentBinder<?, Message> bodyBinder = new DefaultBodyArgumentBinder(beanContext.getConversionService(), deserializer);
        // only the body is bound with the deserializer of the destination, the other arguments as usual
        return new ArgumentBinderRegistry<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <A> Optional<ArgumentBinder<A, Message>> findArgumentBinder(Argument<A> argument) {
                if (argument.getAnnotationMetadata().hasAnnotation(MessageBody.class)) {
                    return Optional.of((ArgumentBinder<A, Message>) bodyBinder);
                }
                return jmsArgumentBinderRegistry.findArgumentBinder(argument);
            }
        };
    }

    private MessageListener generateAndBindListener(Object bean,
                                                    Executable<?, ?> method,
                                                    ArgumentBinderRegistry<Message> binderRegistry,
                                                    String listenerName) {
        // a parameter typed as the JMS session, or a provider subtype, receives the session that consumed the message
        Argument<?>[] sessionArguments = Arrays.stream(method.getArguments())
            .filter(argument -> Session.class.isAssignableFrom(argument.getType())
                && !argument.getAnnotationMetadata().hasStereotype(Bindable.class))
            .toArray(Argument[]::new);
        checkBinders(method, sessionArguments, binderRegistry, listenerName);
        if (sessionArguments.length == 0) {
            return message -> {
                DefaultExecutableBinder<Message> binder = new DefaultExecutableBinder<>();
                BoundExecutable boundExecutable = binder.bind(method, binderRegistry, message);
                boundExecutable.invoke(bean);
            };
        }
        return new SessionAwareMessageListener() {
            @Override
            public void onMessage(Message message, Session session) {
                Map<Argument<?>, Object> preBound = HashMap.newHashMap(sessionArguments.length);
                for (Argument<?> argument : sessionArguments) {
                    preBound.put(argument, session);
                }
                DefaultExecutableBinder<Message> binder = new DefaultExecutableBinder<>(preBound);
                BoundExecutable boundExecutable = binder.bind(method, binderRegistry, message);
                boundExecutable.invoke(bean);
            }

            @Override
            public void checkSession(Session session) {
                for (Argument<?> argument : sessionArguments) {
                    if (!argument.getType().isInstance(session)) {
                        throw new IllegalStateException("The argument '" + argument.getName() + "' of type " + argument.getTypeName()
                            + " of the listener method " + listenerName + " cannot be bound to the session "
                            + session.getClass().getName());
                    }
                }
            }
        };
    }

    /**
     * Checks at registration that every argument other than the session can be bound, since one that can't, such as a
     * parameter of type {@code oracle.jms.AQjmsSession}, which is a {@code javax.jms.Session}, would fail every message.
     */
    private void checkBinders(Executable<?, ?> method, Argument<?>[] sessionArguments,
                              ArgumentBinderRegistry<Message> binderRegistry, String listenerName) {
        List<Argument<?>> bound = Arrays.asList(sessionArguments);
        for (Argument<?> argument : method.getArguments()) {
            if (bound.contains(argument)) {
                continue;
            }
            try {
                binderRegistry.findArgumentBinder(argument);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("The argument '" + argument.getName() + "' of type " + argument.getTypeName()
                    + " of the @" + clazz.getSimpleName() + " listener method " + listenerName + " cannot be bound: annotate it with a binding annotation"
                    + " such as @MessageBody or @MessageHeader, or type it as a jakarta.jms.Session", e);
            }
        }
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
        final int consumers = destinationAnnotation.intValue("consumers").orElse(1);
        if (consumers < 1) {
            throw new IllegalStateException("The listener of " + destination + " must have at least one consumer, but consumers = " + consumers);
        }
        if (consumers > 1 && type == JMSDestinationType.TOPIC && subscription == null) {
            throw new IllegalStateException("The listener of topic " + destination + " has no subscription, so every consumer would receive"
                + " every message: more than one consumer requires a shared subscription");
        }
        if (consumers > 1 && subscription != null && !subscription.shared()) {
            throw new IllegalStateException("The subscription '" + subscription.name() + "' of topic " + destination
                + " is an unshared durable subscription, which allows only one consumer: more than one consumer requires a shared subscription");
        }

        final JMSListenerRegistry registry = beanContext
                .findBean(JMSListenerRegistry.class)
                .orElseThrow(() -> new IllegalStateException("No JMSListenerRegistry configured"));

        final JMSConnectionPool connectionPool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactoryName));
        final Object bean = beanContext.getBean(beanDefinition.getBeanType());
        final ExecutorService executor = getExecutorService(destinationAnnotation);

        // the bean type rather than the declaring type, which may be a superclass of several listeners
        MessageListener listener = generateAndBindListener(bean, method, binderRegistry(destinationAnnotation),
            beanDefinition.getBeanType().getSimpleName() + "#" + method.getMethodName());

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
            // the consumers share one connection, each with its own session; either all of them are registered or none
            for (io.micronaut.jms.listener.JMSListener registeredListener : registry.register(connectionPool, type, destination,
                    transacted, acknowledgeMode, listener, executor, true, messageSelector, subscription, consumers)) {
                registeredListener.addSuccessHandlers(successHandlers);
                registeredListener.addErrorHandlers(errorHandlers);
            }
        } catch (JMSException e) {
            logger.error("Failed to register listener for destination " + destination, e);
        }
    }
}
