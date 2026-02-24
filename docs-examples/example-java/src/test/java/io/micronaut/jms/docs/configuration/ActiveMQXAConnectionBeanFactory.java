package io.micronaut.jms.docs.configuration;

// tag::imports[]
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.activemq.classic.configuration.properties.ActiveMqClassicConfigurationProperties;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import jakarta.inject.Singleton;
import org.apache.activemq.ActiveMQXAConnectionFactory;

// end::imports[]

@Requires(property = "spec.name", value = "CustomBrokerSpec")
// tag::clazz[]
@Factory
class ActiveMQXAConnectionBeanFactory {
    @JMSConnectionFactory("activeMQXAConnectionFactory")
    @Singleton
    ActiveMQXAConnectionFactory createActiveMQXAConnectionFactory(ActiveMqClassicConfigurationProperties amqConfig) {
        return new ActiveMQXAConnectionFactory(amqConfig.getConnectionString());
    }
}
// end::clazz[]
