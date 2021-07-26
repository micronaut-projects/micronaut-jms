package io.micronaut.jms.docs.configuration;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.jms.activemq.classic.configuration.properties.ActiveMqClassicConfigurationProperties;
import jakarta.inject.Singleton;
import org.apache.activemq.ActiveMQXAConnectionFactory;

import javax.jms.ConnectionFactory;
// end::imports[]

@Requires(property = "spec.name", value = "CustomBrokerSpec")
// tag::clazz[]
@Singleton
public class CustomBrokerJMSConnectionPoolListener implements BeanCreatedEventListener<ConnectionFactory> {

    private final ActiveMqClassicConfigurationProperties amqConfig;

    public CustomBrokerJMSConnectionPoolListener(ActiveMqClassicConfigurationProperties amqConfig) {
        this.amqConfig = amqConfig;
    }

    @Override
    public ConnectionFactory onCreated(BeanCreatedEvent<ConnectionFactory> event) {
        return new ActiveMQXAConnectionFactory(amqConfig.getConnectionString());
    }
}
// end::clazz[]
