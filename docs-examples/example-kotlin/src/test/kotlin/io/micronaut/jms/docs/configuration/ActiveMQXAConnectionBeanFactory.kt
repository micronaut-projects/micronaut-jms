package io.micronaut.jms.docs.configuration

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.context.annotation.Factory
import io.micronaut.jms.activemq.classic.configuration.properties.ActiveMqClassicConfigurationProperties
import io.micronaut.jms.annotations.JMSConnectionFactory
import jakarta.inject.Singleton
import org.apache.activemq.ActiveMQXAConnectionFactory

// end::imports[]
@Requires(property = "spec.name", value = "CustomBrokerSpec") // tag::clazz[]
// end::clazz[]
@Factory
internal class ActiveMQXAConnectionBeanFactory {
    @JMSConnectionFactory("activeMQXAConnectionFactory")
    @Singleton
    fun createActiveMQXAConnectionFactory(amqConfig: ActiveMqClassicConfigurationProperties): ActiveMQXAConnectionFactory {
        return ActiveMQXAConnectionFactory(amqConfig.getConnectionString())
    }
} // end::clazz[]
