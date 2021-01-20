package io.micronaut.jms.docs

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jms.model.JMSDestinationType
import io.micronaut.jms.pool.JMSConnectionPool
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.jms.Connection
import javax.jms.Destination
import javax.jms.JMSException
import javax.jms.Session

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import static io.micronaut.jms.model.JMSDestinationType.QUEUE
import static javax.jms.Session.AUTO_ACKNOWLEDGE

abstract class AbstractJmsSpec extends Specification {

    protected final PollingConditions polling = new PollingConditions(timeout: 3)

    protected ApplicationContext applicationContext

    void setup() {
        applicationContext = ApplicationContext.run(getConfiguration(), 'test')
    }

    void cleanup() {
        applicationContext?.close()
    }

    protected Destination lookupDestination(String destination,
                                            JMSDestinationType type) throws JMSException {
        try (Connection connection = connectionPool.createConnection()
             Session session = connection.createSession(false, AUTO_ACKNOWLEDGE)) {
            return type == QUEUE ?
                session.createQueue(destination) :
                session.createTopic(destination)
        }
    }

    protected Map<String, Object> getConfiguration() {
        String broker = UUID.randomUUID().toString().replaceAll('-', '')

        Map<String, Object> config = [:]
        config['micronaut.jms.activemq.classic.connectionString'] = 'vm://' + broker + '?broker.persistent=false'
        config['micronaut.jms.activemq.classic.enabled'] = true
        config['spec.name'] = getClass().getSimpleName()
        config
    }

    protected JMSConnectionPool getConnectionPool() {
        applicationContext.getBean JMSConnectionPool, Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME)
    }
}
