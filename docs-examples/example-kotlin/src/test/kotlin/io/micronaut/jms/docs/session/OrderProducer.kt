package io.micronaut.jms.docs.session

import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody

@Requires(property = "spec.name", value = "SessionSpec")
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface OrderProducer {

    @Queue("orders")
    fun send(@MessageBody order: String)
}
