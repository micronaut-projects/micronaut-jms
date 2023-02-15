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

import java.lang.annotation.Annotation;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import io.micronaut.context.BeanContext;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.bind.JMSArgumentBinderRegistry;
import io.micronaut.jms.listener.*;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.util.Assert;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.messaging.exceptions.MessageAcknowledgementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.jms.Session.CLIENT_ACKNOWLEDGE;

/**
 * Abstract {@link ExecutableMethodProcessor} for annotations related to
 * {@link JMSListener}. Registers a {@link io.micronaut.jms.listener.JMSListenerContainer}
 * if the method annotated with {@code <T>} is part of a bean annotated with {@link JMSListener}.
 *
 * @param <T> the destination type annotation
 * @author Elliott Pope
 * @since 1.0.0
 */
public abstract class AbstractJMSListenerMethodProcessor<S extends Broker, T extends Annotation>
    implements ExecutableMethodProcessor<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final BeanContext beanContext;

    private final JMSArgumentBinderRegistry jmsArgumentBinderRegistry;
    private final Class<T> clazz;
    private final ListenerFactory factory;

    protected AbstractJMSListenerMethodProcessor(BeanContext beanContext,
                                                 JMSArgumentBinderRegistry registry,
                                                 Class<T> clazz, ListenerFactory factory) {
        this.beanContext = beanContext;
        this.jmsArgumentBinderRegistry = registry;
        this.clazz = clazz;
        this.factory = factory;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition,
                        ExecutableMethod<?, ?> method) {

        AnnotationValue<JMSListener> listenerAnnotation = beanDefinition.getAnnotation(JMSListener.class);
        if (listenerAnnotation == null) {
            return;
        }

        String connectionFactoryName = listenerAnnotation.getRequiredValue(String.class);

        AnnotationValue<T> destinationAnnotation = method.getAnnotation(clazz);
        Assert.notNull(destinationAnnotation, () -> "Annotation not found on method " +
            method.getName() + ". Expecting annotation of type " + clazz.getName());

        registerListener(method, connectionFactoryName, beanDefinition, destinationAnnotation, getDestinationType());
    }

    protected abstract ExecutorService getExecutorService(AnnotationValue<T> value);

    protected abstract JMSDestinationType getDestinationType();
    protected abstract S fromAnnotation(ExecutableMethod<?, ?> method, AnnotationValue<T> annotation);

    private void validateArguments(ExecutableMethod<?, ?> method) {
        Stream.of(method.getArguments())
            .filter(arg ->
                arg.isDeclaredAnnotationPresent(MessageBody.class) ||
                arg.isDeclaredAnnotationPresent(io.micronaut.jms.annotations.Message.class))
            .findAny()
            .orElseThrow(() -> new IllegalStateException(
                "Methods annotated with @" + clazz.getSimpleName() +
                    " must have exactly one argument annotated with @MessageBody" +
                    " or @Message"));
    }

    @SuppressWarnings("unchecked")
    private MessageListener generateAndBindListener(Object bean,
                                                    ExecutableMethod<?, ?> method,
                                                    ExecutorService executor,
                                                    boolean acknowledge) {

        return message -> executor.submit(() -> {
            try {
                DefaultExecutableBinder<Message> binder = new DefaultExecutableBinder<>();
                BoundExecutable boundExecutable = binder.bind(method, jmsArgumentBinderRegistry, message);
                boundExecutable.invoke(bean);
                if (acknowledge) {
                    try {
                        message.acknowledge();
                    } catch (JMSException e) {
                        logger.error("Failed to acknowledge receipt of message with the broker. " +
                                "This message may be falsely retried.", e);
                        throw new MessageAcknowledgementException(e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to process a message: " + message + " " + e.getMessage(), e);
            }
        });
    }

    private void registerListener(ExecutableMethod<?, ?> method,
                                  String connectionFactoryName,
                                  BeanDefinition<?> beanDefinition,
                                  AnnotationValue<T> destinationAnnotation,
                                  JMSDestinationType type) {

        validateArguments(method);
        final Object bean = beanContext.findBean(beanDefinition.getBeanType()).get();
        final ExecutorService executor = getExecutorService(destinationAnnotation);

        S broker = fromAnnotation(method, destinationAnnotation);

        MessageListener listener = generateAndBindListener(bean, method, executor,
                CLIENT_ACKNOWLEDGE == broker.getAcknowledgeMode());

        factory.register(connectionFactoryName, broker, listener);
    }
}
