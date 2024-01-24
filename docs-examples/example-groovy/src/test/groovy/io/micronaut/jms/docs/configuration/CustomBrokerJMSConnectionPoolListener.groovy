package io.micronaut.jms.docs.configuration

// tag::imports[]
import groovy.transform.CompileStatic
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.jms.activemq.classic.configuration.properties.ActiveMqClassicConfigurationProperties
import jakarta.inject.Singleton
import org.apache.activemq.ActiveMQXAConnectionFactory

import jakarta.jms.ConnectionFactory
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = 'spec.name', value = 'CustomBrokerSpec')
// tag::clazz[]
@CompileStatic
@Singleton
class CustomBrokerJMSConnectionPoolListener implements BeanCreatedEventListener<ConnectionFactory> {

    private final ActiveMqClassicConfigurationProperties amqConfig

    CustomBrokerJMSConnectionPoolListener(ActiveMqClassicConfigurationProperties amqConfig) {
        this.amqConfig = amqConfig
    }

    @Override
    ConnectionFactory onCreated(BeanCreatedEvent<ConnectionFactory> event) {
        new ActiveMQXAConnectionFactory(amqConfig.getConnectionString())
    }
}
// end::clazz[]
