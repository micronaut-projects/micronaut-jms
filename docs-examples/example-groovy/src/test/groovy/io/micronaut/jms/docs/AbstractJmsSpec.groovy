package io.micronaut.jms.docs

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.jms.pool.JMSConnectionPool
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME

abstract class AbstractJmsSpec extends Specification {

    protected final static PollingConditions polling = new PollingConditions(timeout: 3)

    protected ApplicationContext applicationContext

    void setup() {
        applicationContext = ApplicationContext.run(getConfiguration(), 'test')
    }

    void cleanup() {
        applicationContext?.close()
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
