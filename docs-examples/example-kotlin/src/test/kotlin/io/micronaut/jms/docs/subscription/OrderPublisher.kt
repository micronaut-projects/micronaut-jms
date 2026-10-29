package io.micronaut.jms.docs.subscription

import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Topic
import io.micronaut.messaging.annotation.MessageBody

@Requires(property = "spec.name", value = "DurableSubscriptionSpec")
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface OrderPublisher {

    @Topic("orders")
    fun publish(@MessageBody order: String)
}
