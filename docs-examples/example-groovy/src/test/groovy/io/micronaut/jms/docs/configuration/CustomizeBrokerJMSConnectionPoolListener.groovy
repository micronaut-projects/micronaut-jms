package io.micronaut.jms.docs.configuration

// tag::imports[]
import groovy.transform.CompileStatic
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import org.apache.activemq.ActiveMQConnectionFactory

import javax.inject.Singleton
import javax.jms.ConnectionFactory
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = 'spec.name', value = 'CustomizeBrokerSpec')
// tag::clazz[]
@CompileStatic
@Singleton
class CustomizeBrokerJMSConnectionPoolListener implements BeanCreatedEventListener<ConnectionFactory> {

    @Override
    ConnectionFactory onCreated(BeanCreatedEvent<ConnectionFactory> event) {

        ConnectionFactory connectionFactory = event.bean
        if (connectionFactory instanceof ActiveMQConnectionFactory) {
            connectionFactory.useAsyncSend = true
        }

        return connectionFactory
    }
}
// end::clazz[]
