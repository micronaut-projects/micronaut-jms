package io.micronaut.jms.docs.binding

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.jms.annotations.JMSProducer

// tag::imports[]

import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
import io.micronaut.messaging.annotation.MessageHeader

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import static io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID

// end::imports[]

@Requires(property = 'spec.name', value = 'BindingSpec')
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface MapProducer {

    @Queue('queue_map')
    void send(@MessageBody Map<String, Serializable> body,
              @MessageHeader(JMS_CORRELATION_ID) @Nullable String correlationId,
              @MessageHeader('CustomStringHeader') @Nullable String stringHeader,
              @MessageHeader('CustomBooleanHeader') boolean booleanHeader,
              @MessageHeader('CustomByteHeader') byte byteHeader,
              @MessageHeader('CustomShortHeader') short shortHeader,
              @MessageHeader('CustomIntegerHeader') int intHeader,
              @MessageHeader('CustomLongHeader') long longHeader,
              @MessageHeader('CustomFloatHeader') float floatHeader,
              @MessageHeader('CustomDoubleHeader') double doubleHeader)
}
// end::clazz[]
