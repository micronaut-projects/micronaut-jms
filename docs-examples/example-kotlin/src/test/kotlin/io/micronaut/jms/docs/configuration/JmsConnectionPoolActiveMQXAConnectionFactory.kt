package io.micronaut.jms.docs.configuration

import io.micronaut.context.annotation.Requires
//tag::imports[]
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties
import io.micronaut.jms.pool.JMSConnectionPool
import jakarta.inject.Singleton
import org.apache.activemq.ActiveMQXAConnectionFactory

//end::imports[]

@Requires(property = "spec.name", value = "CustomBrokerSpec")
//tag::class[]
@Factory
internal class JmsConnectionPoolActiveMQXAConnectionFactory(val properties: JMSConfigurationProperties) {
    @EachBean(ActiveMQXAConnectionFactory::class)
    @Singleton
    @Primary
    fun createJmsConnectionPool(connectionFactory: ActiveMQXAConnectionFactory): JMSConnectionPool {
        return JMSConnectionPool(connectionFactory, properties.getInitialPoolSize(), properties.getMaxPoolSize())
    }
} //end::clazz[]
