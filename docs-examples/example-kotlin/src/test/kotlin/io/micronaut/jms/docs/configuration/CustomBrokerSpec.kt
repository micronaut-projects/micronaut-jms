package io.micronaut.jms.docs.configuration

import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jms.docs.AbstractJmsSpec
import io.micronaut.jms.pool.JMSConnectionPool
import io.micronaut.jms.pool.PooledConnection
import jakarta.jms.XAConnection
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomBrokerSpec : AbstractJmsSpec() {

    @Test
    fun testCustomBroker() {
        val connectionPool = applicationContext.getBean(
            JMSConnectionPool::class.java,
            Qualifiers.byName("activeMQXAConnectionFactory")
        )

        val connection = connectionPool.createConnection()
        assertTrue(connection is PooledConnection)

        val realConnection = (connection as PooledConnection).get()
        assertTrue(realConnection is XAConnection)
    }
}
