package io.micronaut.jms.docs.configuration;

import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.docs.AbstractJmsSpec;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import jakarta.jms.ConnectionFactory;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomizeBrokerSpec extends AbstractJmsSpec {

    @Test
    void testCustomizeBroker() {

        ConnectionFactory connectionFactory = applicationContext.getBean(
            ConnectionFactory.class,
            Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME));

        assertTrue(connectionFactory instanceof ActiveMQConnectionFactory);

        ActiveMQConnectionFactory amqcf = (ActiveMQConnectionFactory) connectionFactory;

        assertTrue(amqcf.isUseAsyncSend());
    }
}
