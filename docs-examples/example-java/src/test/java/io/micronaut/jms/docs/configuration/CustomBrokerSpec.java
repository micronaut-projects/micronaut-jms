package io.micronaut.jms.docs.configuration;

import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.docs.AbstractJmsSpec;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.pool.PooledConnection;
import jakarta.jms.ConnectionFactory;
import org.junit.jupiter.api.Test;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.XAConnection;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomBrokerSpec extends AbstractJmsSpec {

    @Test
    void testCustomBroker() throws JMSException {

        JMSConnectionPool connectionPool = applicationContext.getBean(
            JMSConnectionPool.class,
            Qualifiers.byName("activeMQXAConnectionFactory"));

        Connection connection = connectionPool.createConnection();
        assertTrue(connection instanceof PooledConnection);

        Connection realConnection = ((PooledConnection) connection).get();
        assertTrue(realConnection instanceof XAConnection);
    }
}
