package io.micronaut.jms.docs.session

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
import jakarta.jms.Session

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
// end::imports[]

@Requires(property = 'spec.name', value = 'SessionSpec')
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class OrderListener {

    final List<String> confirmations = Collections.synchronizedList([])

    @Queue(value = 'orders', transacted = true)
    void receive(@MessageBody String order, Session session) { // <1>
        session.createProducer(session.createQueue('order_confirmations')).withCloseable { producer -> // <2>
            producer.send(session.createTextMessage("confirmed ${order}".toString()))
        }
    }

    @Queue('order_confirmations')
    void confirmed(@MessageBody String confirmation) {
        confirmations << confirmation
    }
}
// end::clazz[]
