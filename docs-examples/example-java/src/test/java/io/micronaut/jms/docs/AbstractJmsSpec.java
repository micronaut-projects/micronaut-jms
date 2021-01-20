package io.micronaut.jms.docs;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static io.micronaut.jms.model.JMSDestinationType.QUEUE;
import static javax.jms.Session.AUTO_ACKNOWLEDGE;

public abstract class AbstractJmsSpec {

    protected ApplicationContext applicationContext;

    @BeforeEach
    void setup() {
        applicationContext = ApplicationContext.run(getConfiguration(), "test");
    }

    @AfterEach
    void cleanup() {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    protected Destination lookupDestination(String destination,
                                            JMSDestinationType type) throws JMSException {
        try (Connection connection = getConnectionPool().createConnection();
             Session session = connection.createSession(false, AUTO_ACKNOWLEDGE)) {
            return type == QUEUE ?
                session.createQueue(destination) :
                session.createTopic(destination);
        }
    }

    protected Map<String, Object> getConfiguration() {
        String broker = UUID.randomUUID().toString().replaceAll("-", "");

        Map<String, Object> config = new HashMap<>();
        config.put("micronaut.jms.activemq.classic.connectionString", "vm://" + broker + "?broker.persistent=false");
        config.put("micronaut.jms.activemq.classic.enabled", true);
        config.put("spec.name", getClass().getSimpleName());
        return config;
    }

    protected JMSConnectionPool getConnectionPool() {
        return applicationContext.getBean(JMSConnectionPool.class, Qualifiers.byName(CONNECTION_FACTORY_BEAN_NAME));
    }
}
