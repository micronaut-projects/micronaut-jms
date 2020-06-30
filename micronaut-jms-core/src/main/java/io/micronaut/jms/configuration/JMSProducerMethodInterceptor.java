package io.micronaut.jms.configuration;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.Header;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.model.MessageHeader;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.templates.JmsProducer;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

@Singleton
public class JMSProducerMethodInterceptor implements MethodInterceptor<Object, Object> {

    @Inject
    private BeanContext beanContext;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.hasAnnotation(JMSProducer.class)) {
            ExecutableMethod<?, ?> method = context.getExecutableMethod();
            String connectionFactory = method.getAnnotation(JMSProducer.class).getValue(String.class)
                    .orElseThrow(() -> new RuntimeException("@JMSProducer must specify a connection factory."));

            if (method.hasAnnotation(Queue.class)) {
                AnnotationValue<Queue> queueAnnotation = method.getAnnotation(Queue.class);
                String queueName = queueAnnotation.get("destination", String.class)
                        .orElseThrow(() -> new RuntimeException("@Queue must specify a destination."));

                MessageHeader[] headers = Arrays.stream(method.getArguments())
                        .filter(arg -> arg.isDeclaredAnnotationPresent(Header.class))
                        .map(arg -> {
                            String headerName = arg.getAnnotation(Header.class)
                                    .getValue(String.class)
                                    .orElse(null);
                            String headerValue = String.valueOf(context.getParameterValueMap().get(arg.getName()));
                            return new MessageHeader(headerName, headerValue);
                        }).toArray(MessageHeader[]::new);

                String messageArgumentName = Arrays.stream(method.getArguments())
                        .filter(arg -> arg.getAnnotationMetadata().isEmpty())
                        .map(Argument::getName)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("At least one argument must not have an annotation present"));

                JMSConnectionPool pool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactory));

                JmsProducer producer = new JmsProducer(JMSDestinationType.QUEUE);
                producer.setConnectionPool(pool);
                producer.setSerializer(new DefaultSerializerDeserializer());

                producer.send(queueName, context.getParameterValueMap().get(messageArgumentName), headers);
                return null;
            }

            if (method.hasAnnotation(Topic.class)) {
                AnnotationValue<Topic> queueAnnotation = method.getAnnotation(Topic.class);
                String queueName = queueAnnotation.get("destination", String.class)
                        .orElseThrow(() -> new RuntimeException("@Queue must specify a destination."));

                MessageHeader[] headers = Arrays.stream(method.getArguments())
                        .filter(arg -> arg.isDeclaredAnnotationPresent(Header.class))
                        .map(arg -> {
                            String headerName = arg.getAnnotation(Header.class)
                                    .getValue(String.class)
                                    .orElse(null);
                            String headerValue = String.valueOf(context.getParameterValueMap().get(arg.getName()));
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
