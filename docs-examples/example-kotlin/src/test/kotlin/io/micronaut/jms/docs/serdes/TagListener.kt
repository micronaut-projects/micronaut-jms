package io.micronaut.jms.docs.serdes

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
import java.util.concurrent.CopyOnWriteArrayList
// end::imports[]

@Requires(property = "spec.name", value = "SerdesSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class TagListener {

    val received: MutableList<List<String>> = CopyOnWriteArrayList()

    @Queue(value = "tags", deserializer = "csv") // <1>
    fun receive(@MessageBody tags: List<String>) {
        received.add(tags)
    }
}
// end::clazz[]
