package io.micronaut.jms.docs.delivery

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.MessagePriority
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.model.JMSDeliveryMode
import io.micronaut.messaging.annotation.MessageBody

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
// end::imports[]

@Requires(property = 'spec.name', value = 'DeliverySpec')
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface AlertProducer {

    @Queue(value = 'alerts', deliveryMode = JMSDeliveryMode.NON_PERSISTENT, priority = 7) // <1>
    void send(@MessageBody String alert)

    @Queue(value = 'alerts', deliveryMode = JMSDeliveryMode.NON_PERSISTENT)
    void sendWithPriority(@MessageBody String alert, @MessagePriority int priority) // <2>
}
// end::clazz[]
