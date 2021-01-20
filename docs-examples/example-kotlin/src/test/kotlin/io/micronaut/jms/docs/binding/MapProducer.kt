package io.micronaut.jms.docs.binding

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID
import io.micronaut.messaging.annotation.Body
import io.micronaut.messaging.annotation.Header
import java.io.Serializable
// end::imports[]

@Requires(property = "spec.name", value = "BindingSpec")
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface MapProducer {
    @Queue("queue_map")
    fun send(
        @Body body: Map<String, Serializable?>,
        @Header(JMS_CORRELATION_ID) correlationId: String?,
        @Header("CustomStringHeader") stringHeader: String?,
        @Header("CustomBooleanHeader") booleanHeader: Boolean,
        @Header("CustomByteHeader") byteHeader: Byte,
        @Header("CustomShortHeader") shortHeader: Short,
        @Header("CustomIntegerHeader") intHeader: Int,
        @Header("CustomLongHeader") longHeader: Long,
        @Header("CustomFloatHeader") floatHeader: Float,
        @Header("CustomDoubleHeader") doubleHeader: Double
    )
}
// end::clazz[]
