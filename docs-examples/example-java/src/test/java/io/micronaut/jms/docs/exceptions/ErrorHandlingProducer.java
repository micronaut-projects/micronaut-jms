package io.micronaut.jms.docs.exceptions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Requires(property = "spec.name", value = "ErrorHandlingSpec")
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
public interface ErrorHandlingProducer {
    @Queue("error-queue")
    void send(@MessageBody String body);
}
