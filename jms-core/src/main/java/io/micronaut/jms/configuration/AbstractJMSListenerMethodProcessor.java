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
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.bind.JMSArgumentBinderRegistry;
import io.micronaut.jms.listener.JMSListenerContainerFactory;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.util.Assert;
import io.micronaut.messaging.annotation.Body;
import io.micronaut.messaging.exceptions.MessageAcknowledgementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.lang.annotation.Annotation;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import static javax.jms.Session.CLIENT_ACKNOWLEDGE;

/***
 *
 * Abstract {@link ExecutableMethodProcessor} for annotations related to {@link JMSListener}. If the method annotated
 *      with {@param <T>} is not part of a bean annotated with {@link JMSListener} then the processor will take
 *      no action. If the method is part of a {@link JMSListener} then the processor will register a
 *      {@link io.micronaut.jms.listener.JMSListenerContainer}
 *
 * @param <T>
 */
public abstract class AbstractJMSListenerMethodProcessor<T extends Annotation>
    implements ExecutableMethodProcessor<T> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final BeanContext beanContext;

    private final DefaultExecutableBinder<Message> binder = new DefaultExecutableBinder<>();
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

        registerListener(method, connectionFactoryName, beanDefinition,
            destinationAnnotation, getDestinationType());
    }

    protected abstract ExecutorService getExecutorService(AnnotationValue<T> value);

    protected abstract JMSDestinationType getDestinationType();

    private void validateArguments(ExecutableMethod<?, ?> method) {
        Stream.of(method.getArguments())
            .filter(arg ->
                arg.isDeclaredAnnotationPresent(Body.class) ||
                arg.isDeclaredAnnotationPresent(io.micronaut.jms.annotations.Message.class))
            .findAny()
            .orElseThrow(() -> new IllegalStateException(
                "Methods annotated with @" + clazz.getSimpleName() +
                    " must have exactly one argument annotated with @Body" +
                    " or @Message"));
    }

    @SuppressWarnings("unchecked")
    private MessageListener generateAndBindListener(Object bean,
                                                    ExecutableMethod<?, ?> method,
                                                    ExecutorService executor,
                                                    boolean acknowledge) {

        return message -> executor.submit(() -> {
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
        });
    }

    private void registerListener(ExecutableMethod<?, ?> method,
                                  String connectionFactoryName,
                                  BeanDefinition<?> beanDefinition,
                                  AnnotationValue<T> destinationAnnotation,
                                  JMSDestinationType type) {

        validateArguments(method);

        final Class<?> targetClass = Stream.of(method.getArguments())
            .filter(arg ->
                arg.isDeclaredAnnotationPresent(Body.class) ||
                arg.isDeclaredAnnotationPresent(io.micronaut.jms.annotations.Message.class))
            .findAny()
            .map(Argument::getClass)
            .get();

        final String destination = destinationAnnotation.getRequiredValue(String.class);
        final int acknowledgeMode = destinationAnnotation.getRequiredValue("acknowledgeMode", Integer.class);
        final boolean transacted = destinationAnnotation.getRequiredValue("transacted", Boolean.class);

        final JMSListenerContainerFactory listenerFactory = beanContext
            .findBean(JMSListenerContainerFactory.class)
            .orElseThrow(() -> new IllegalStateException("No JMSListenerFactory configured"));

        final JMSConnectionPool connectionPool = beanContext.getBean(
            JMSConnectionPool.class, Qualifiers.byName(connectionFactoryName));

        final Object bean = beanContext.findBean(beanDefinition.getBeanType()).get();

        final ExecutorService executor = getExecutorService(destinationAnnotation);

        MessageListener listener = generateAndBindListener(bean, method, executor,
            CLIENT_ACKNOWLEDGE == acknowledgeMode);

        listenerFactory.registerListener(
            connectionPool,
            destination,
            listener,
            targetClass,
            transacted,
            acknowledgeMode,
            type);
    }
}
