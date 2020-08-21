package io.micronaut.jms.configuration;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.model.MessageHeader;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.templates.JmsProducer;
import io.micronaut.messaging.annotation.Header;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Map;

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
        if (context.hasAnnotation(JMSProducer.class)) {
            ExecutableMethod<?, ?> method = context.getExecutableMethod();
            String connectionFactory = method.stringValue(JMSProducer.class)
                    .orElseThrow(() -> new ConfigurationException("@JMSProducer must specify a connection factory."));

            if (method.hasAnnotation(Queue.class)) {
                String queueName = method.stringValue(Queue.class)
                        .orElseThrow(() -> new ConfigurationException("@Queue must specify a destination."));

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

                String messageArgumentName = Arrays.stream(method.getArguments())
                        .filter(arg -> arg.getAnnotationMetadata().isEmpty())
                        .map(Argument::getName)
                        .findFirst()
                        .orElseThrow(() -> new ConfigurationException("At least one argument must not have an annotation present"));

                JMSConnectionPool pool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactory));

                JmsProducer producer = new JmsProducer(JMSDestinationType.QUEUE);
                producer.setConnectionPool(pool);
                producer.setSerializer(new DefaultSerializerDeserializer());

                producer.send(queueName, context.getParameterValueMap().get(messageArgumentName), headers);
                return null;
            }

            if (method.hasAnnotation(Topic.class)) {
                String queueName = method.stringValue(Topic.class)
                        .orElseThrow(() -> new RuntimeException("@Queue must specify a destination."));

                Map<String, Object> parameterValueMap = context.getParameterValueMap();

                MessageHeader[] headers = Arrays.stream(method.getArguments())
                        .filter(arg -> arg.isDeclaredAnnotationPresent(Header.class))
                        .map(arg -> {
                            String headerName = arg.getAnnotation(Header.class)
                                    .getValue(String.class)
                                    .orElse(null);
                            String headerValue = String.valueOf(parameterValueMap.get(arg.getName()));
                            return new MessageHeader(headerName, headerValue);
                        }).toArray(MessageHeader[]::new);

                String messageArgumentName = Arrays.stream(method.getArguments())
                        .filter(arg -> arg.getAnnotationMetadata().isEmpty())
                        .map(Argument::getName)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("At least one argument must not have an annotation present"));

                JMSConnectionPool pool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactory));

                JmsProducer producer = new JmsProducer(JMSDestinationType.TOPIC);
                producer.setConnectionPool(pool);
                producer.setSerializer(new DefaultSerializerDeserializer());

                producer.send(queueName, context.getParameterValueMap().get(messageArgumentName), headers);
                return null;
            }
        }
        return context.proceed();
    }
}
