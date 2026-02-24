package io.micronaut.jms.docs.configuration;

import io.micronaut.context.annotation.Requires;
//tag::imports[]
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.inject.Singleton;
import org.apache.activemq.ActiveMQXAConnectionFactory;

//end::imports[]
@Requires(property = "spec.name", value = "CustomBrokerSpec")
//tag::clazz[]
@Factory
class JmsConnectionPoolActiveMQXAConnectionFactory {
    private final JMSConfigurationProperties properties;
    JmsConnectionPoolActiveMQXAConnectionFactory(JMSConfigurationProperties properties) {
        this.properties = properties;
    }

    @EachBean(ActiveMQXAConnectionFactory.class)
    @Singleton
    @Primary
    JMSConnectionPool createJmsConnectionPool(ActiveMQXAConnectionFactory connectionFactory) {
        return new JMSConnectionPool(connectionFactory, properties.getInitialPoolSize(), properties.getMaxPoolSize());
    }
}
//end::clazz[]
