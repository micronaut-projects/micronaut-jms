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
import io.micronaut.jms.annotations.MessageDeliveryMode;
import io.micronaut.jms.annotations.MessagePriority;
import io.micronaut.jms.annotations.MessageTTL;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.model.JMSDeliveryMode;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.model.MessageHeader;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.serdes.Serializer;
import io.micronaut.jms.templates.JmsProducer;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.messaging.exceptions.MessagingClientException;
import jakarta.inject.Singleton;

import jakarta.jms.Message;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;
import static io.micronaut.jms.model.JMSDestinationType.TOPIC;
import static jakarta.jms.Message.DEFAULT_TIME_TO_LIVE;

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

        long timeToLive = timeToLive(method, parameterValueMap);
        JMSDeliveryMode deliveryMode;
        int priority;
        try {
            deliveryMode = deliveryMode(method, annotationType, parameterValueMap);
            priority = priority(method, annotationType, parameterValueMap);
        } catch (IllegalArgumentException e) {
            // like a priority out of range, which the producer rejects
            throw new MessagingClientException("Problem sending message to " + destinationName + ": " + e.getMessage(), e);
        }
        JMSConnectionPool pool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactory));

        JmsProducer producer = new JmsProducer(destinationType, pool, serializer);
        producer.send(destinationName, body, deliveryMode, priority, timeToLive, headers);

        return null;
    }

    private static JMSDeliveryMode deliveryMode(ExecutableMethod<?, ?> method,
                                                Class<? extends Annotation> annotationType,
                                                Map<String, Object> parameterValueMap) {
        return Arrays.stream(method.getArguments())
            .filter(arg -> arg.isDeclaredAnnotationPresent(MessageDeliveryMode.class))
            .map(arg -> parameterValueMap.get(arg.getName()))
            .filter(Objects::nonNull)
            .map(JMSProducerMethodInterceptor::toDeliveryMode)
            .findFirst()
            .orElseGet(() -> method.enumValue(annotationType, "deliveryMode", JMSDeliveryMode.class).orElse(JMSDeliveryMode.PERSISTENT));
    }

    private static JMSDeliveryMode toDeliveryMode(Object value) {
        if (value instanceof JMSDeliveryMode mode) {
            return mode;
        }
        if (value instanceof Number number) {
            return JMSDeliveryMode.from(number.intValue());
        }
        if (value instanceof CharSequence text) {
            // the name, as listeners accept for the JMSDeliveryMode header, or the DeliveryMode int
            String name = text.toString().trim();
            if (!name.isEmpty() && name.chars().allMatch(Character::isDigit)) {
                return JMSDeliveryMode.from(Integer.parseInt(name));
            }
            try {
                return JMSDeliveryMode.valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("No JMSDeliveryMode named " + name + ", expected NON_PERSISTENT or PERSISTENT", e);
            }
        }
        throw new IllegalArgumentException("A @MessageDeliveryMode argument must be a JMSDeliveryMode, a jakarta.jms.DeliveryMode int "
            + "or the name of a delivery mode, but was a " + value.getClass().getName());
    }

    private static int priority(ExecutableMethod<?, ?> method,
                                Class<? extends Annotation> annotationType,
                                Map<String, Object> parameterValueMap) {
        return Arrays.stream(method.getArguments())
            .filter(arg -> arg.isDeclaredAnnotationPresent(MessagePriority.class))
            .map(arg -> parameterValueMap.get(arg.getName()))
            .filter(Objects::nonNull)
            .map(JMSProducerMethodInterceptor::toPriority)
            .findFirst()
            .orElseGet(() -> method.intValue(annotationType, "priority").orElse(Message.DEFAULT_PRIORITY));
    }

    private static int toPriority(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof CharSequence text) {
            String digits = text.toString().trim();
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("A @MessagePriority argument must be a number from 0 to 9, but was " + digits, e);
            }
        }
        throw new IllegalArgumentException("A @MessagePriority argument must be a number or a String of digits, but was a "
            + value.getClass().getName());
    }

    private long timeToLive(ExecutableMethod<?, ?> method, Map<String, Object> parameterValueMap) {
        return Arrays.stream(method.getArguments())
            .filter(arg -> arg.isDeclaredAnnotationPresent(MessageTTL.class))
            .map(arg -> parameterValueMap.get(arg.getName()))
            .map(arg -> (arg instanceof Number n) ? n.longValue() : null)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(DEFAULT_TIME_TO_LIVE);
    }
}
