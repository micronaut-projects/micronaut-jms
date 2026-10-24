package io.micronaut.jms.docs.delivery

import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.model.JMSHeaders.JMS_DELIVERY_MODE
import io.micronaut.jms.model.JMSHeaders.JMS_PRIORITY
import io.micronaut.messaging.annotation.MessageBody
import io.micronaut.messaging.annotation.MessageHeader
import java.util.Collections

@Requires(property = "spec.name", value = "DeliverySpec")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class AlertListener {

    val received: MutableList<String> = Collections.synchronizedList(ArrayList())

    @Queue("alerts")
    fun receive(@MessageBody alert: String,
                @MessageHeader(JMS_PRIORITY) priority: Int,
                @MessageHeader(JMS_DELIVERY_MODE) deliveryMode: String) {
        received.add("$alert $priority $deliveryMode")
    }
}
