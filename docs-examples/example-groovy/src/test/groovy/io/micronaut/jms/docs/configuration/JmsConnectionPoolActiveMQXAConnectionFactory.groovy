package io.micronaut.jms.docs.configuration

import io.micronaut.context.annotation.Requires

//tag::imports[]
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.core.annotation.Internal
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties
import io.micronaut.jms.pool.JMSConnectionPool
import jakarta.inject.Singleton
import org.apache.activemq.ActiveMQXAConnectionFactory

//tag::imports[]

@Requires(property = 'spec.name', value = 'CustomBrokerSpec')
//tag::class[]
@Factory
@Internal
class JmsConnectionPoolActiveMQXAConnectionFactory {
    private final JMSConfigurationProperties properties

    /**
     * @param properties JMS Configuration
     */
    JmsConnectionPoolActiveMQXAConnectionFactory(JMSConfigurationProperties properties) {
        this.properties = properties
    }

    /**
     * Creates a {@link JMSConnectionPool} from each registered {@link ActiveMQXAConnectionFactory} in the context.
     * @param connectionFactory Connection Factory
     * @return JMS Connection Pool
     */
    @EachBean(ActiveMQXAConnectionFactory.class)
    @Singleton
    @Primary
    JMSConnectionPool createJmsConnectionPool(ActiveMQXAConnectionFactory connectionFactory) {
        return new JMSConnectionPool(connectionFactory, properties.getInitialPoolSize(), properties.getMaxPoolSize())
    }
}
//end::class[]
