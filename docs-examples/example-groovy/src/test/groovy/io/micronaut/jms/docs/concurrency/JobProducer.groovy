package io.micronaut.jms.docs.concurrency

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
// end::imports[]

@Requires(property = 'spec.name', value = 'ConcurrencySpec')
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface JobProducer {

    @Queue(value = 'jobs', transacted = true) // <1>
    void submit(@MessageBody String job)
}
// end::clazz[]
