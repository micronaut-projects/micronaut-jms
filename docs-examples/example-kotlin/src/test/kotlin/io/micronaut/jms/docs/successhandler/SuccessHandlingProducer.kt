package io.micronaut.jms.docs.successhandler

import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody

@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface SuccessHandlingProducer {
    @Queue("success-queue")
    fun push(@MessageBody message: String)
}