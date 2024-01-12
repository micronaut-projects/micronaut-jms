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
import io.micronaut.messaging.annotation.MessageBody
import io.micronaut.messaging.annotation.MessageHeader
import java.io.Serializable
import jakarta.jms.Destination
// end::imports[]

@Requires(property = "spec.name", value = "BindingSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class MapConsumer {

    val messageBodies = mutableListOf<Map<String, Serializable>>()
    val messageHeaders = mutableListOf<Map<String, Any?>>()
    val messages = mutableListOf<jakarta.jms.Message>()

    @Queue(value = "queue_map", concurrency = "1-5")
    fun receive(
        @MessageBody body: Map<String, Serializable>,
        @Message message: jakarta.jms.Message,
        @MessageHeader(JMS_CORRELATION_ID) correlationId: String?,
        @MessageHeader(JMS_DELIVERY_MODE) deliveryMode: Int,
        @MessageHeader(JMS_DESTINATION) destination: Destination?,
        @MessageHeader(JMS_EXPIRATION) expiration: Long,
        @MessageHeader(JMS_MESSAGE_ID) messageId: String?,
        @MessageHeader(JMS_PRIORITY) priority: Int,
        @MessageHeader(JMS_REDELIVERED) redelivered: Boolean,
        @MessageHeader(JMS_REPLY_TO) replyTo: Destination?,
        @MessageHeader(JMS_TIMESTAMP) timestamp: Long,
        @MessageHeader(JMS_TYPE) type: String?,
        @MessageHeader("CustomStringHeader") stringHeader: String?,
        @MessageHeader("CustomBooleanHeader") booleanHeader: Boolean,
        @MessageHeader("CustomByteHeader") byteHeader: Byte,
        @MessageHeader("CustomShortHeader") shortHeader: Short,
        @MessageHeader("CustomIntegerHeader") intHeader: Int,
        @MessageHeader("CustomLongHeader") longHeader: Long,
        @MessageHeader("CustomFloatHeader") floatHeader: Float,
        @MessageHeader("CustomDoubleHeader") doubleHeader: Double
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
