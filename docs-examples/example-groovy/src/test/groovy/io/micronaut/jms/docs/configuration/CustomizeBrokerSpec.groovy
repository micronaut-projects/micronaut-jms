package io.micronaut.jms.docs.configuration

import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jms.docs.AbstractJmsSpec
import io.micronaut.jms.pool.JMSConnectionPool
import org.apache.activemq.ActiveMQConnectionFactory

import javax.jms.ConnectionFactory

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME

class CustomizeBrokerSpec extends AbstractJmsSpec {

    void 'test customize broker'() {
        when:
        ConnectionFactory connectionFactory = applicationContext.getBean(
            ConnectionFactory,
            Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME))

        then:
        connectionFactory instanceof JMSConnectionPool
        ((JMSConnectionPool)connectionFactory).connectionFactory instanceof ActiveMQConnectionFactory
        ((ActiveMQConnectionFactory)((JMSConnectionPool)connectionFactory).connectionFactory).useAsyncSend
    }
}
