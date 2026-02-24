package io.micronaut.jms.docs.configuration

import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.docs.AbstractJmsSpec
import org.apache.activemq.ActiveMQConnectionFactory
import jakarta.jms.ConnectionFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomizeBrokerSpec : AbstractJmsSpec() {

    @Test
    fun testCustomizeBroker() {
        val connectionFactories = applicationContext.getBeansOfType(
            ConnectionFactory::class.java,
            Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME)
        )

        assertTrue(connectionFactories.any { it is ActiveMQConnectionFactory })

        val amqcf = connectionFactories.first { it is ActiveMQConnectionFactory } as ActiveMQConnectionFactory
        assertTrue(amqcf.isUseAsyncSend)
    }
}
