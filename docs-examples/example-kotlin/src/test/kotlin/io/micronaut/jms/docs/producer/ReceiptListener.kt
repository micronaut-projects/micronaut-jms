package io.micronaut.jms.docs.producer

import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
import java.util.Collections

@Requires(property = "spec.name", value = "AsyncProducerSpec")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class ReceiptListener {

    val received: MutableList<String> = Collections.synchronizedList(ArrayList())

    @Queue("receipts")
    fun receive(@MessageBody receipt: String) {
        received.add(receipt)
    }
}
