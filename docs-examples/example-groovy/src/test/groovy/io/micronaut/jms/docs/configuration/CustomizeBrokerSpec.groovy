package io.micronaut.jms.docs.configuration

import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jms.docs.AbstractJmsSpec
import org.apache.activemq.ActiveMQConnectionFactory

import jakarta.jms.ConnectionFactory

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME

class CustomizeBrokerSpec extends AbstractJmsSpec {

    void 'test customize broker'() {
        when:
        Collection<ConnectionFactory> connectionFactories = applicationContext.getBeansOfType(
            ConnectionFactory,
            Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME))

        then:
        connectionFactories.stream().anyMatch(ActiveMQConnectionFactory.class::isInstance)

        when:
        ActiveMQConnectionFactory connectionFactory = (ActiveMQConnectionFactory) connectionFactories.stream()
                .filter(ActiveMQConnectionFactory.class::isInstance).findFirst()
                .get();

        then:
        connectionFactory instanceof ActiveMQConnectionFactory
        ((ActiveMQConnectionFactory) connectionFactory).useAsyncSend
    }
}
