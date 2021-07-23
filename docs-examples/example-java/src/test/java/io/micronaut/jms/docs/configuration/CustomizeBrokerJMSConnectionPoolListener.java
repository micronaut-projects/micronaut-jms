package io.micronaut.jms.docs.configuration;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.ConnectionFactory;
// end::imports[]

@Requires(property = "spec.name", value = "CustomizeBrokerSpec")
// tag::clazz[]
@Singleton
public class CustomizeBrokerJMSConnectionPoolListener implements BeanCreatedEventListener<ConnectionFactory> {

    @Override
    public ConnectionFactory onCreated(BeanCreatedEvent<ConnectionFactory> event) {

        ConnectionFactory connectionFactory = event.getBean();
        if (connectionFactory instanceof ActiveMQConnectionFactory) {
            ActiveMQConnectionFactory amqcf = (ActiveMQConnectionFactory) connectionFactory;
            amqcf.setUseAsyncSend(true);
        }

        return connectionFactory;
    }
}
// end::clazz[]
