package io.micronaut.jms.docs.configuration

import groovy.transform.CompileStatic
import io.micronaut.jms.annotations.JMSConnectionFactory

// tag::imports[]
import jakarta.inject.Singleton
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.properties.ActiveMqClassicConfigurationProperties
import org.apache.activemq.ActiveMQXAConnectionFactory
// end::imports[]

@Requires(property = 'spec.name', value = 'CustomBrokerSpec')
// tag::clazz[]
@Factory
@CompileStatic
class ActiveMQXAConnectionBeanFactory {
    @JMSConnectionFactory("activeMQXAConnectionFactory")
    @Singleton
    ActiveMQXAConnectionFactory createActiveMQXAConnectionFactory(ActiveMqClassicConfigurationProperties amqConfig) {
        new ActiveMQXAConnectionFactory(amqConfig.getConnectionString())
    }
}
// end::clazz[]
