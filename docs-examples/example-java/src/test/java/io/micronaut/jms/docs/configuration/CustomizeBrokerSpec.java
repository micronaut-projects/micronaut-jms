package io.micronaut.jms.docs.configuration;

import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.docs.AbstractJmsSpec;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import jakarta.jms.ConnectionFactory;

import java.util.Collection;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomizeBrokerSpec extends AbstractJmsSpec {

    @Test
    void testCustomizeBroker() {

        Collection<ConnectionFactory> connectionFactories = applicationContext.getBeansOfType(
            ConnectionFactory.class,
            Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME));

        assertTrue(connectionFactories.stream().anyMatch(ActiveMQConnectionFactory.class::isInstance));

        ActiveMQConnectionFactory amqcf = (ActiveMQConnectionFactory) connectionFactories.stream()
            .filter(ActiveMQConnectionFactory.class::isInstance).findFirst()
            .get();
        assertTrue(amqcf.isUseAsyncSend());
    }
}
