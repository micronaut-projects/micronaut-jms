package io.micronaut.jms.docs.serdes

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
// end::imports[]

@Requires(property = "spec.name", value = "SerdesSpec")
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface TagProducer {

    @Queue(value = "tags", serializer = "csv") // <1>
    fun send(@MessageBody tags: List<String>)
}
// end::clazz[]
