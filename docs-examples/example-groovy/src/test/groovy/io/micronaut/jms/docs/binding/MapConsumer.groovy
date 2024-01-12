package io.micronaut.jms.docs.binding


// tag::imports[]
import io.micronaut.core.annotation.Nullable
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Message
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
import io.micronaut.messaging.annotation.MessageHeader

import jakarta.jms.Destination

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import static io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID
import static io.micronaut.jms.model.JMSHeaders.JMS_DELIVERY_MODE
import static io.micronaut.jms.model.JMSHeaders.JMS_DESTINATION
import static io.micronaut.jms.model.JMSHeaders.JMS_EXPIRATION
import static io.micronaut.jms.model.JMSHeaders.JMS_MESSAGE_ID
import static io.micronaut.jms.model.JMSHeaders.JMS_PRIORITY
import static io.micronaut.jms.model.JMSHeaders.JMS_REDELIVERED
import static io.micronaut.jms.model.JMSHeaders.JMS_REPLY_TO
import static io.micronaut.jms.model.JMSHeaders.JMS_TIMESTAMP
import static io.micronaut.jms.model.JMSHeaders.JMS_TYPE
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = 'spec.name', value = 'BindingSpec')
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class MapConsumer {

    List<Map<String, Serializable>> messageBodies = [].asSynchronized()
    List<Map<String, Object>> messageHeaders =  [].asSynchronized()
    List<javax.jms.Message> messages =  [].asSynchronized()

    @Queue(value = 'queue_map', concurrency = '1-5')
    void receive(@MessageBody Map<String, Serializable> body,
                 @Message javax.jms.Message message,
                 @MessageHeader(JMS_CORRELATION_ID) @Nullable String correlationId,
                 @MessageHeader(JMS_DELIVERY_MODE) int deliveryMode,
                 @MessageHeader(JMS_DESTINATION) Destination destination,
                 @MessageHeader(JMS_EXPIRATION) long expiration,
                 @MessageHeader(JMS_MESSAGE_ID) String messageId,
                 @MessageHeader(JMS_PRIORITY) int priority,
                 @MessageHeader(JMS_REDELIVERED) boolean redelivered,
                 @MessageHeader(JMS_REPLY_TO) @Nullable Destination replyTo,
                 @MessageHeader(JMS_TIMESTAMP) long timestamp,
                 @MessageHeader(JMS_TYPE) @Nullable String type,
                 @MessageHeader('CustomStringHeader') @Nullable String stringHeader,
                 @MessageHeader('CustomBooleanHeader') boolean booleanHeader,
                 @MessageHeader('CustomByteHeader') byte byteHeader,
                 @MessageHeader('CustomShortHeader') short shortHeader,
                 @MessageHeader('CustomIntegerHeader') int intHeader,
                 @MessageHeader('CustomLongHeader') long longHeader,
                 @MessageHeader('CustomFloatHeader') float floatHeader,
                 @MessageHeader('CustomDoubleHeader') double doubleHeader) {

        Map<String, Object> headerValues = [
                (JMS_CORRELATION_ID): correlationId,
                (JMS_DELIVERY_MODE): deliveryMode,
                (JMS_DESTINATION): destination,
                (JMS_EXPIRATION): expiration,
                (JMS_MESSAGE_ID): messageId,
                (JMS_PRIORITY): priority,
                (JMS_REDELIVERED): redelivered,
                (JMS_REPLY_TO): replyTo,
                (JMS_TIMESTAMP): timestamp,
                (JMS_TYPE): type,
                CustomStringHeader: stringHeader,
                CustomBooleanHeader: booleanHeader,
                CustomByteHeader: byteHeader,
                CustomShortHeader: shortHeader,
                CustomIntegerHeader: intHeader,
                CustomLongHeader: longHeader,
                CustomFloatHeader: floatHeader,
                CustomDoubleHeader: doubleHeader]

        messageHeaders << headerValues
        messageBodies << body
        messages << message
    }
}
// end::clazz[]
