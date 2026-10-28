package io.micronaut.jms.docs.subscription;

// tag::imports[]
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.messaging.annotation.MessageBody;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
// end::imports[]

@Requires(property = "spec.name", value = "DurableSubscriptionSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class AuditListener {

    final List<String> audited = new CopyOnWriteArrayList<>();

    @Topic(value = "orders", subscription = "order-audit", durable = true) // <1>
    public void audit(@MessageBody String order) {
        audited.add(order);
    }
}
// end::clazz[]
