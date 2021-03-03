package io.micronaut.jms.docs.exceptions

import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.Body
import java.lang.RuntimeException

@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class ErrorThrowingConsumer {

    val processed: MutableList<String> = mutableListOf()

    @Queue("error-queue")
    fun receive(@Body message: String) {
        if (message == "throw an error") {
            throw RuntimeException("This is an unexpected error.")
        }
        processed.add(message)
    }
}