package io.micronaut.jms.docs.configuration

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import jakarta.inject.Singleton
import org.apache.activemq.ActiveMQConnectionFactory
import javax.jms.ConnectionFactory
// end::imports[]

@Requires(property = "spec.name", value = "CustomizeBrokerSpec")
// tag::clazz[]
@Singleton
class CustomizeBrokerJMSConnectionPoolListener : BeanCreatedEventListener<ConnectionFactory?> {

    override fun onCreated(event: BeanCreatedEvent<ConnectionFactory?>): ConnectionFactory? {
        val connectionFactory = event.bean
        if (connectionFactory is ActiveMQConnectionFactory) {
            connectionFactory.isUseAsyncSend = true
        }
        return connectionFactory
    }
}
// end::clazz[]
