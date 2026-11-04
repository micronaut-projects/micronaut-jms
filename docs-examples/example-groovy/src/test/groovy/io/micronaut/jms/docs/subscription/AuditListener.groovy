package io.micronaut.jms.docs.subscription

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Topic
import io.micronaut.messaging.annotation.MessageBody

import java.util.concurrent.CopyOnWriteArrayList

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
// end::imports[]

@Requires(property = 'spec.name', value = 'DurableSubscriptionSpec')
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class AuditListener {

    final List<String> audited = new CopyOnWriteArrayList<>()

    @Topic(value = 'orders', subscription = 'order-audit', durable = true) // <1>
    void audit(@MessageBody String order) {
        audited << order
    }
}
// end::clazz[]
