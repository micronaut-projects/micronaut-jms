package io.micronaut.jms.docs.configuration;

import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.docs.AbstractJmsSpec;
import io.micronaut.jms.pool.JMSConnectionPool;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;

import javax.jms.ConnectionFactory;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomizeBrokerSpec extends AbstractJmsSpec {

    @Test
    void testCustomizeBroker() {

        ConnectionFactory connectionFactory = applicationContext.getBean(
            ConnectionFactory.class,
            Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME));

        assertTrue(connectionFactory instanceof JMSConnectionPool);

        JMSConnectionPool pool = (JMSConnectionPool) connectionFactory;

        assertTrue(pool.getConnectionFactory() instanceof ActiveMQConnectionFactory);

        ActiveMQConnectionFactory amqcf = (ActiveMQConnectionFactory) pool.getConnectionFactory();

        assertTrue(amqcf.isUseAsyncSend());
    }
}
