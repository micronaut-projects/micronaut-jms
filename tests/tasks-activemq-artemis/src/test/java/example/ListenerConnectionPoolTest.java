package example;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.jms.listener.JMSListenerRegistry;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.jms.Connection;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest
class ListenerConnectionPoolTest implements TestPropertyProvider {

    private static final String QUEUE = "listener-connection-pool";
    // the application's own listener and scheduled publisher also borrow from this pool
    private static final int MAX_POOL_SIZE = 4;

    @Inject
    @Named(CONNECTION_FACTORY_BEAN_NAME)
    JMSConnectionPool connectionPool;

    @Test
    void shutdownReturnsListenerConnectionsToThePool() throws Exception {
        JMSListenerRegistry registry = new JMSListenerRegistry(Collections.emptyList(), Collections.emptyList());
        AtomicInteger received = new AtomicInteger();
        // more registrations than the pool holds: each must give its connection back once the listeners shut down
        for (int i = 0; i < MAX_POOL_SIZE * 3; i++) {
            registry.register(connectionPool.createConnection(), JMSDestinationType.QUEUE, QUEUE, false,
                Session.AUTO_ACKNOWLEDGE, message -> received.incrementAndGet(), null, true, Optional.empty());
            registry.shutdown();
        }

        registry.register(connectionPool.createConnection(), JMSDestinationType.QUEUE, QUEUE, false,
            Session.AUTO_ACKNOWLEDGE, message -> received.incrementAndGet(), null, true, Optional.empty());
        try (Connection connection = connectionPool.createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageProducer producer = session.createProducer(session.createQueue(QUEUE))) {
            producer.send(session.createTextMessage("after re-registration"));
        }
        await().atMost(30, SECONDS).until(() -> received.get() == 1);
        registry.shutdown();
    }

    @Override
    public @NonNull Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>(ActiveMqArtemis.getProperties());
        properties.put("micronaut.jms.max-pool-size", String.valueOf(MAX_POOL_SIZE));
        return properties;
    }
}
