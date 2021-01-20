package io.micronaut.jms.docs.configuration;

import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.docs.AbstractJmsSpec;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.pool.PooledConnection;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.XAConnection;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomBrokerSpec extends AbstractJmsSpec {

    @Test
    void testCustomBroker() throws JMSException {

        JMSConnectionPool connectionPool = applicationContext.getBean(
            JMSConnectionPool.class,
            Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME));

        Connection connection = connectionPool.createConnection();
        assertTrue(connection instanceof PooledConnection);

        Connection realConnection = ((PooledConnection) connection).get();
        assertTrue(realConnection instanceof XAConnection);
    }
}
