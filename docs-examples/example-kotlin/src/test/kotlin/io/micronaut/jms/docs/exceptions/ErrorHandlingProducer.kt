package io.micronaut.jms.docs.exceptions

import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.Body

@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface ErrorHandlingProducer {
    @Queue("error-queue")
    fun push(@Body message: String)
}