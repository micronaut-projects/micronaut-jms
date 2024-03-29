package io.micronaut.jms.docs.quickstart

// tag::imports[]
// end::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
import java.util.*

@Requires(property = "spec.name", value = "QuickstartSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME) // <1>
class TextConsumer {

    val messages: MutableList<String> = Collections.synchronizedList(ArrayList())

    @Queue(value = "queue_text") // <2>
    fun receive(@MessageBody body: String) { // <3>
        messages.add(body)
    }
}
// end::clazz[]
