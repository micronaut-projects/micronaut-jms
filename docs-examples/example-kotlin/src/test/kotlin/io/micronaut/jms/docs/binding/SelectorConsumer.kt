package io.micronaut.jms.docs.binding

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.annotations.Topic
import io.micronaut.messaging.annotation.MessageBody
import java.util.Collections
// end::imports[]

@Requires(property = "spec.name", value = "SelectorSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class SelectorConsumer {

    val messageBodiesTrue: MutableList<String> = Collections.synchronizedList(ArrayList())

    val messageBodiesFalse: MutableList<String> = Collections.synchronizedList(ArrayList())

    val messageBodiesTopic: MutableList<String> = Collections.synchronizedList(ArrayList())

    @Queue(value = "selector_queue", messageSelector = "CustomBooleanHeader=true")
    fun receive(@MessageBody body: String) {
        messageBodiesTrue.add(body)
    }

    @Queue(value = "selector_queue", messageSelector = "CustomBooleanHeader=false")
    fun receive2(@MessageBody body: String) {
        messageBodiesFalse.add(body)
    }

    @Topic(value = "selector_topic", messageSelector = "CustomBooleanHeader=true")
    fun receiveTopic(@MessageBody body: String) {
        messageBodiesTopic.add(body)
    }
}
// end::clazz[]
