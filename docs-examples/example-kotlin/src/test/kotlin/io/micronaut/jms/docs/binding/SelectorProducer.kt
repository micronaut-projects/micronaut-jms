package io.micronaut.jms.docs.binding

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.annotations.Topic
import io.micronaut.messaging.annotation.MessageBody
import io.micronaut.messaging.annotation.MessageHeader
// end::imports[]

@Requires(property = "spec.name", value = "SelectorSpec")
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface SelectorProducer {

    @Queue(value = "selector_queue")
    fun sendQueue(@MessageBody body: String, @MessageHeader("CustomBooleanHeader") booleanHeader: Boolean)

    @Topic(value = "selector_topic")
    fun sendTopic(@MessageBody body: String, @MessageHeader("CustomBooleanHeader") booleanHeader: Boolean)
}
// end::clazz[]
