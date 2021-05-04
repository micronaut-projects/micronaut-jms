package io.micronaut.jms.docs.quickstart

// tag::imports[]
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = 'spec.name', value = 'QuickstartSpec')
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME) // <1>
class TextConsumer {

    List<String> messages = [].asSynchronized()

    @Queue(value = 'queue_text', concurrency = '1-5') // <2>
    void receive(@MessageBody String body) { // <3>
        messages << body
    }
}
// end::clazz[]
