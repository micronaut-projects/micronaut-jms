package io.micronaut.jms.docs.configuration;

import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.docs.AbstractJmsKotest
import io.micronaut.jms.pool.JMSConnectionPool
import io.micronaut.jms.pool.PooledConnection
import jakarta.jms.XAConnection

class CustomBrokerSpec : AbstractJmsKotest({

    val specName = javaClass.simpleName

    given("Using a custom broker") {
        val applicationContext = startContext(specName)

        `when`("Accessing the connection pool") {
            val connectionPool = applicationContext.getBean(
                JMSConnectionPool::class.java,
                Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME));
            val connection = connectionPool.createConnection()
            val realConnection = (connection as PooledConnection).get();

            then("The expected customization is in effect") {
                realConnection.shouldBeInstanceOf<XAConnection>()
            }
        }

        applicationContext.stop()
    }
})
