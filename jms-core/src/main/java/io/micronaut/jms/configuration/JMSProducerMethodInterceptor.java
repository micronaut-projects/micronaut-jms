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
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.model.MessageHeader;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.templates.JmsProducer;
import io.micronaut.messaging.annotation.Header;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Map;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;
import static io.micronaut.jms.model.JMSDestinationType.TOPIC;

/***
 * A {@link MethodInterceptor} providing the implementation for sending messages to a broker.
 *      Requires that the interface be annotated with {@link JMSProducer} and have at least one method
 *      annotated with {@link Queue} or {@link Topic}.
 *
 * @author elliott
 * @since 1.0
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
        String connectionFactory = method.stringValue(JMSProducer.class)
            .orElseThrow(() -> new ConfigurationException(
                "@JMSProducer must specify a connection factory."));

        if (method.hasAnnotation(Queue.class)) {
            String queueName = method.stringValue(Queue.class)
                .orElseThrow(() -> new ConfigurationException(
                    "@Queue must specify a destination."));

            String messageArgumentName = Arrays.stream(method.getArguments())
                .filter(arg -> arg.getAnnotationMetadata().isEmpty())
                .map(Argument::getName)
                .findFirst()
                .orElseThrow(() -> new ConfigurationException(
                    "At least one argument must not have an annotation present"));

            Map<String, Object> parameterValueMap = context.getParameterValueMap();

            MessageHeader[] headers = Arrays.stream(method.getArguments())
                .filter(arg -> arg.isDeclaredAnnotationPresent(Header.class))
                .map(arg -> {
                    String headerName = arg.getAnnotation(Header.class)
                        .stringValue()
                        .orElse(null);
                    String headerValue = String.valueOf(parameterValueMap.get(arg.getName()));
                    return new MessageHeader(headerName, headerValue);
                }).toArray(MessageHeader[]::new);

            JMSConnectionPool pool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactory));

            JmsProducer producer = new JmsProducer(QUEUE);
            producer.setConnectionPool(pool);
            producer.setSerializer(new DefaultSerializerDeserializer());

            producer.send(queueName, context.getParameterValueMap().get(messageArgumentName), headers);
            return null;
        }

        if (method.hasAnnotation(Topic.class)) {
            String topicName = method.stringValue(Topic.class)
                .orElseThrow(() -> new RuntimeException("@Queue must specify a destination."));

            String messageArgumentName = Arrays.stream(method.getArguments())
                .filter(arg -> arg.getAnnotationMetadata().isEmpty())
                .map(Argument::getName)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                    "At least one argument must not have an annotation present"));

            Map<String, Object> parameterValueMap = context.getParameterValueMap();

            MessageHeader[] headers = Arrays.stream(method.getArguments())
                .filter(arg -> arg.isDeclaredAnnotationPresent(Header.class))
                .map(arg -> {
                    String headerName = arg.getAnnotation(Header.class)
                        .stringValue()
                        .orElse(null);
                    String headerValue = String.valueOf(parameterValueMap.get(arg.getName()));
                    return new MessageHeader(headerName, headerValue);
                }).toArray(MessageHeader[]::new);

            JMSConnectionPool pool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactory));

            JmsProducer producer = new JmsProducer(TOPIC);
            producer.setConnectionPool(pool);
            producer.setSerializer(new DefaultSerializerDeserializer());

            producer.send(topicName, context.getParameterValueMap().get(messageArgumentName), headers);
            return null;
        }
        return context.proceed();
    }
}
