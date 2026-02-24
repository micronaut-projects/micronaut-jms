package io.micronaut.jms.docs

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.pool.JMSConnectionPool
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID

abstract class AbstractJmsSpec {

    protected lateinit var applicationContext: ApplicationContext

    @BeforeEach
    fun setup() {
        applicationContext = ApplicationContext.run(getConfiguration(), "test")
    }

    @AfterEach
    fun cleanup() {
        if (::applicationContext.isInitialized) {
            applicationContext.close()
        }
    }

    protected open fun getConfiguration(): Map<String, Any> {
        val broker = UUID.randomUUID().toString().replace("-", "")
        return mutableMapOf(
            "micronaut.jms.activemq.classic.connectionString" to "vm://$broker?broker.persistent=false",
            "micronaut.jms.activemq.classic.enabled" to true,
            "micronaut.jms.activemq.classic.username" to "activemq",
            "micronaut.jms.activemq.classic.password" to "activemq",
            "spec.name" to javaClass.simpleName
        )
    }

    protected fun getConnectionPool(): JMSConnectionPool {
        return applicationContext.getBean(JMSConnectionPool::class.java, Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME))
    }
}
