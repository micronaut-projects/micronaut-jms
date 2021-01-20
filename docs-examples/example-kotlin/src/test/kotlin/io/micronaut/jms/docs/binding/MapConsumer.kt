package io.micronaut.jms.docs.binding

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Message
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID
import io.micronaut.jms.model.JMSHeaders.JMS_DELIVERY_MODE
import io.micronaut.jms.model.JMSHeaders.JMS_DESTINATION
import io.micronaut.jms.model.JMSHeaders.JMS_EXPIRATION
import io.micronaut.jms.model.JMSHeaders.JMS_MESSAGE_ID
import io.micronaut.jms.model.JMSHeaders.JMS_PRIORITY
import io.micronaut.jms.model.JMSHeaders.JMS_REDELIVERED
import io.micronaut.jms.model.JMSHeaders.JMS_REPLY_TO
import io.micronaut.jms.model.JMSHeaders.JMS_TIMESTAMP
import io.micronaut.jms.model.JMSHeaders.JMS_TYPE
import io.micronaut.messaging.annotation.Body
import io.micronaut.messaging.annotation.Header
import java.io.Serializable
import javax.jms.Destination
// end::imports[]

@Requires(property = "spec.name", value = "BindingSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class MapConsumer {

    val messageBodies = mutableListOf<Map<String, Serializable>>()
    val messageHeaders = mutableListOf<Map<String, Any?>>()
    val messages = mutableListOf<javax.jms.Message>()

    @Queue(value = "queue_map", concurrency = "1-5")
    fun receive(
        @Body body: Map<String, Serializable>,
        @Message message: javax.jms.Message,
        @Header(JMS_CORRELATION_ID) correlationId: String?,
        @Header(JMS_DELIVERY_MODE) deliveryMode: Int,
        @Header(JMS_DESTINATION) destination: Destination?,
        @Header(JMS_EXPIRATION) expiration: Long,
        @Header(JMS_MESSAGE_ID) messageId: String?,
        @Header(JMS_PRIORITY) priority: Int,
        @Header(JMS_REDELIVERED) redelivered: Boolean,
        @Header(JMS_REPLY_TO) replyTo: Destination?,
        @Header(JMS_TIMESTAMP) timestamp: Long,
        @Header(JMS_TYPE) type: String?,
        @Header("CustomStringHeader") stringHeader: String?,
        @Header("CustomBooleanHeader") booleanHeader: Boolean,
        @Header("CustomByteHeader") byteHeader: Byte,
        @Header("CustomShortHeader") shortHeader: Short,
        @Header("CustomIntegerHeader") intHeader: Int,
        @Header("CustomLongHeader") longHeader: Long,
        @Header("CustomFloatHeader") floatHeader: Float,
        @Header("CustomDoubleHeader") doubleHeader: Double
    ) {
        val headerValues = mapOf(
            JMS_CORRELATION_ID to correlationId,
            JMS_DELIVERY_MODE to deliveryMode,
            JMS_DESTINATION to destination,
            JMS_EXPIRATION to expiration,
            JMS_MESSAGE_ID to messageId,
            JMS_PRIORITY to priority,
            JMS_REDELIVERED to redelivered,
            JMS_REPLY_TO to replyTo,
            JMS_TIMESTAMP to timestamp,
            JMS_TYPE to type,
            "CustomStringHeader" to stringHeader,
            "CustomBooleanHeader" to booleanHeader,
            "CustomByteHeader" to byteHeader,
            "CustomShortHeader" to shortHeader,
            "CustomIntegerHeader" to intHeader,
            "CustomLongHeader" to longHeader,
            "CustomFloatHeader" to floatHeader,
            "CustomDoubleHeader" to doubleHeader)
        messageHeaders.add(headerValues)
        messageBodies.add(body)
        messages.add(message)
    }
}
// end::clazz[]
