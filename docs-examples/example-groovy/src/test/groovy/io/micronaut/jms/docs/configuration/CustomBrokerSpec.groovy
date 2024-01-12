package io.micronaut.jms.docs.configuration

import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jms.docs.AbstractJmsSpec
import io.micronaut.jms.pool.JMSConnectionPool
import io.micronaut.jms.pool.PooledConnection

import jakarta.jms.Connection
import jakarta.jms.XAConnection

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME

class CustomBrokerSpec extends AbstractJmsSpec {

    void 'test custom broker'() {
        when:
        JMSConnectionPool connectionPool = applicationContext.getBean(
            JMSConnectionPool,
            Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME))

        Connection connection = connectionPool.createConnection()

        then:
        connection instanceof PooledConnection

        when:
        Connection realConnection = ((PooledConnection) connection).get()

        then:
        realConnection instanceof XAConnection

// tag::producer[]
// end::producer[]
    }
}
