package io.micronaut.jms.docs.binding

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
import io.micronaut.messaging.annotation.MessageHeader
import io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID
import java.io.Serializable
// end::imports[]

@Requires(property = "spec.name", value = "BindingSpec")
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface MapProducer {
    @Queue("queue_map")
    fun send(
        @MessageBody body: Map<String, Serializable?>,
        @MessageHeader(JMS_CORRELATION_ID) correlationId: String?,
        @MessageHeader("CustomStringHeader") stringHeader: String?,
        @MessageHeader("CustomBooleanHeader") booleanHeader: Boolean,
        @MessageHeader("CustomByteHeader") byteHeader: Byte,
        @MessageHeader("CustomShortHeader") shortHeader: Short,
        @MessageHeader("CustomIntegerHeader") intHeader: Int,
        @MessageHeader("CustomLongHeader") longHeader: Long,
        @MessageHeader("CustomFloatHeader") floatHeader: Float,
        @MessageHeader("CustomDoubleHeader") doubleHeader: Double
    )
}
// end::clazz[]
