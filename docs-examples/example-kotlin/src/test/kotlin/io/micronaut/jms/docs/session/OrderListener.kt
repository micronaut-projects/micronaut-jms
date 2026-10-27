package io.micronaut.jms.docs.session

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
import jakarta.jms.Session
import java.util.Collections
// end::imports[]

@Requires(property = "spec.name", value = "SessionSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class OrderListener {

    val confirmations: MutableList<String> = Collections.synchronizedList(ArrayList())

    @Queue(value = "orders", transacted = true)
    fun receive(@MessageBody order: String, session: Session) { // <1>
        session.createProducer(session.createQueue("order_confirmations")).use { producer -> // <2>
            producer.send(session.createTextMessage("confirmed $order"))
        }
    }

    @Queue("order_confirmations")
    fun confirmed(@MessageBody confirmation: String) {
        confirmations.add(confirmation)
    }
}
// end::clazz[]
