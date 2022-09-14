package io.micronaut.jms.docs.configuration;

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.docs.AbstractJmsKotest
import io.micronaut.jms.pool.JMSConnectionPool
import org.apache.activemq.ActiveMQConnectionFactory
import javax.jms.ConnectionFactory

class CustomizeBrokerSpec : AbstractJmsKotest({

    val specName = javaClass.simpleName

    given("Using a customized broker") {
        val applicationContext = startContext(specName)

        `when`("Accessing the connection factory") {
            val connectionFactory = applicationContext.getBean(
                ConnectionFactory::class.java,
                Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME))

            then("The expected customization is in effect") {
                connectionFactory.shouldBeInstanceOf<JMSConnectionPool>()
                val pool: JMSConnectionPool = connectionFactory
                pool.getConnectionFactory().shouldBeInstanceOf<ActiveMQConnectionFactory>()

                val amqcf: ActiveMQConnectionFactory = pool.getConnectionFactory() as ActiveMQConnectionFactory
                amqcf.isUseAsyncSend() shouldBe true
            }
        }

        applicationContext.stop()
    }
})
