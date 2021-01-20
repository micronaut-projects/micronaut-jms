package io.micronaut.jms.docs.binding

// tag::imports[]
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Message
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.Body
import io.micronaut.messaging.annotation.Header

import javax.annotation.Nullable
import javax.jms.Destination

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
    void receive(@Body Map<String, Serializable> body,
                 @Message javax.jms.Message message,
                 @Header(JMS_CORRELATION_ID) @Nullable String correlationId,
                 @Header(JMS_DELIVERY_MODE) int deliveryMode,
                 @Header(JMS_DESTINATION) Destination destination,
                 @Header(JMS_EXPIRATION) long expiration,
                 @Header(JMS_MESSAGE_ID) String messageId,
                 @Header(JMS_PRIORITY) int priority,
                 @Header(JMS_REDELIVERED) boolean redelivered,
                 @Header(JMS_REPLY_TO) @Nullable Destination replyTo,
                 @Header(JMS_TIMESTAMP) long timestamp,
                 @Header(JMS_TYPE) @Nullable String type,
                 @Header('CustomStringHeader') @Nullable String stringHeader,
                 @Header('CustomBooleanHeader') boolean booleanHeader,
                 @Header('CustomByteHeader') byte byteHeader,
                 @Header('CustomShortHeader') short shortHeader,
                 @Header('CustomIntegerHeader') int intHeader,
                 @Header('CustomLongHeader') long longHeader,
                 @Header('CustomFloatHeader') float floatHeader,
                 @Header('CustomDoubleHeader') double doubleHeader) {

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
