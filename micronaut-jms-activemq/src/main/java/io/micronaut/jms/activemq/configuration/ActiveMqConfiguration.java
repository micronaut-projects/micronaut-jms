package io.micronaut.jms.activemq.configuration;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.activemq.configuration.properties.ActiveMqConfigurationProperties;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.ConnectionFactory;

@Factory
@Requires(property = "io.micronaut.configuration.jms.activemq.enabled", value = "true")
public class ActiveMqConfiguration {

    /***
     *
     * Generates a {@link JMSConnectionFactory} bean in the application context.
     *
     * The bean is a simply configured {@link ActiveMQConnectionFactory} configured with properties
     *      from {@link ActiveMqConfigurationProperties}
     *
     * @param configuration
     *
     * @return the {@link ActiveMQConnectionFactory} defined by the {@param configuration}
     */
    @JMSConnectionFactory("activeMqConnectionFactory")
    public ConnectionFactory activeMqConnectionFactory(ActiveMqConfigurationProperties configuration) {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(configuration.getConnectionString());
        return connectionFactory;
    }
}
