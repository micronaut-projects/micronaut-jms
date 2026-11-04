package io.micronaut.jms.docs.delivery

import io.micronaut.context.annotation.Requires
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
import io.micronaut.messaging.annotation.MessageHeader

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import static io.micronaut.jms.model.JMSHeaders.JMS_DELIVERY_MODE
import static io.micronaut.jms.model.JMSHeaders.JMS_PRIORITY

@Requires(property = 'spec.name', value = 'DeliverySpec')
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class AlertListener {

    final List<String> received = Collections.synchronizedList([])

    @Queue('alerts')
    void receive(@MessageBody String alert,
                 @MessageHeader(JMS_PRIORITY) int priority,
                 @MessageHeader(JMS_DELIVERY_MODE) String deliveryMode) {
        received << "${alert} ${priority} ${deliveryMode}".toString()
    }
}
