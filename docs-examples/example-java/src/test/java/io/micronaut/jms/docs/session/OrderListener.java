package io.micronaut.jms.docs.session;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
// end::imports[]

@Requires(property = "spec.name", value = "SessionSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class OrderListener {

    final List<String> confirmations = Collections.synchronizedList(new ArrayList<>());

    @Queue("orders")
    public void receive(@MessageBody String order, Session session) throws JMSException { // <1>
        try (MessageProducer producer = session.createProducer(session.createQueue("order_confirmations"))) { // <2>
            producer.send(session.createTextMessage("confirmed " + order));
        }
    }

    @Queue("order_confirmations")
    public void confirmed(@MessageBody String confirmation) {
        confirmations.add(confirmation);
    }
}
// end::clazz[]
