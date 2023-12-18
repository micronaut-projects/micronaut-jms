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

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.MessageTTL;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.model.MessageHeader;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.serdes.Serializer;
import io.micronaut.jms.templates.JmsProducer;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Map;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;
import static io.micronaut.jms.model.JMSDestinationType.TOPIC;
import static javax.jms.Message.DEFAULT_TIME_TO_LIVE;

/**
 * Sends messages to a broker. Requires that the interface be annotated with
 * {@link JMSProducer} and have at least one method annotated with
 * {@link Queue} or {@link Topic}.
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
@Singleton
public class JMSProducerMethodInterceptor implements MethodInterceptor<Object, Object> {

    private final BeanContext beanContext;

    public JMSProducerMethodInterceptor(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {

        if (!context.hasAnnotation(JMSProducer.class)) {
            return context.proceed();
        }

        ExecutableMethod<?, ?> method = context.getExecutableMethod();

        Class<? extends Annotation> annotationType;
        JMSDestinationType destinationType;
        if (method.hasAnnotation(Queue.class)) {
            annotationType = Queue.class;
            destinationType = QUEUE;
        } else if (method.hasAnnotation(Topic.class)) {
            annotationType = Topic.class;
            destinationType = TOPIC;
        } else {
            return context.proceed();
        }

        String connectionFactory = method.stringValue(JMSProducer.class)
            .orElseThrow(() -> new ConfigurationException(
                "@JMSProducer must specify a connection factory."));

        String destinationName = method.stringValue(annotationType)
            .orElseThrow(() -> new ConfigurationException(
                "@" + annotationType.getSimpleName() + " must specify a destination."));

        Map<String, Object> parameterValueMap = context.getParameterValueMap();

        Object body = Arrays.stream(method.getArguments())
            .filter(arg -> arg.isDeclaredAnnotationPresent(MessageBody.class))
            .map(arg -> parameterValueMap.get(arg.getName()))
            .findFirst()
            .orElseThrow(() -> new ConfigurationException(
                "One method argument must be annotated with @Body"));

        String serializerName = method.stringValue(annotationType, "serializer").orElse(null);
        Serializer serializer = serializerName == null
            ? beanContext.getBean(DefaultSerializerDeserializer.class)
            : beanContext.getBean(Serializer.class, Qualifiers.byName(serializerName));

        MessageHeader[] headers = Arrays.stream(method.getArguments())
            .filter(arg -> arg.isDeclaredAnnotationPresent(io.micronaut.messaging.annotation.MessageHeader.class))
            .map(arg -> {
                String argName = arg.getName();
                String headerName = arg.getAnnotationMetadata().stringValue(io.micronaut.messaging.annotation.MessageHeader.class)
                    .orElseThrow(() -> new IllegalArgumentException(
                        "@Header annotation on argument '" + argName + "' must have a name"));
                return new MessageHeader(headerName, parameterValueMap.get(argName));
            }).toArray(MessageHeader[]::new);

        long timeToLive = Arrays.stream(method.getArguments())
            .filter(arg -> arg.isDeclaredAnnotationPresent(MessageTTL.class))
            .map(arg -> parameterValueMap.get(arg.getName()))
            .map(arg -> (Long) arg)
            .findFirst()
            .orElse(DEFAULT_TIME_TO_LIVE);

        JMSConnectionPool pool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactory));

        JmsProducer producer = new JmsProducer(destinationType, pool, serializer);
        producer.send(destinationName, body, timeToLive, headers);

        return null;
    }
}
