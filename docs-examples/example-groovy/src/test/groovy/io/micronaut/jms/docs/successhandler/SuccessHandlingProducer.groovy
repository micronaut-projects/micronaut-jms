package io.micronaut.jms.docs.successhandler

import io.micronaut.context.annotation.Requires
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME

@Requires(property = "spec.name", value = 'SuccessHandlingSpec')
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface SuccessHandlingProducer {
    @Queue("success-queue")
    void send(@MessageBody String body);
}
