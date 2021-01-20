package io.micronaut.jms.docs.configuration

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.jms.activemq.classic.configuration.properties.ActiveMqClassicConfigurationProperties
import org.apache.activemq.ActiveMQXAConnectionFactory
import javax.inject.Singleton
import javax.jms.ConnectionFactory
// end::imports[]

@Requires(property = "spec.name", value = "CustomBrokerSpec")
// tag::clazz[]
@Singleton
class CustomBrokerJMSConnectionPoolListener(
    private val amqConfig: ActiveMqClassicConfigurationProperties) :
    BeanCreatedEventListener<ConnectionFactory> {

    override fun onCreated(event: BeanCreatedEvent<ConnectionFactory>) =
        ActiveMQXAConnectionFactory(amqConfig.connectionString)
}
// end::clazz[]
