package io.micronaut.jms.docs.binding

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.Body
import io.micronaut.messaging.annotation.Header

import javax.annotation.Nullable

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import static io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID
// end::imports[]

@Requires(property = 'spec.name', value = 'BindingSpec')
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface MapProducer {

    @Queue('queue_map')
    void send(@Body Map<String, Serializable> body,
              @Header(JMS_CORRELATION_ID) @Nullable String correlationId,
              @Header('CustomStringHeader') @Nullable String stringHeader,
              @Header('CustomBooleanHeader') boolean booleanHeader,
              @Header('CustomByteHeader') byte byteHeader,
              @Header('CustomShortHeader') short shortHeader,
              @Header('CustomIntegerHeader') int intHeader,
              @Header('CustomLongHeader') long longHeader,
              @Header('CustomFloatHeader') float floatHeader,
              @Header('CustomDoubleHeader') double doubleHeader)
}
// end::clazz[]
