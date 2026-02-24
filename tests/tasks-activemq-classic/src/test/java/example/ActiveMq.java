package example;

import org.testcontainers.activemq.ActiveMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class ActiveMq {

    private static final String IMAGE_NAME = "apache/activemq-classic:latest";
    private static ActiveMQContainer container;

    public static Map<String, String> getProperties() {
        if (container == null) {
            container = new ActiveMQContainer(IMAGE_NAME);
            container.waitingFor(
                Wait.forLogMessage(".*Apache ActiveMQ.*started.*", 1)
                    .withStartupTimeout(Duration.ofSeconds(60))
            );
            container.start();
        }
        return getProperties(container);
    }

    private static Map<String, String> getProperties(ActiveMQContainer container) {
        return new HashMap<>(Map.of(
            "micronaut.jms.activemq.classic.connection-string", container.getBrokerUrl(),
            "micronaut.jms.activemq.classic.username", "testcontainers",
            "micronaut.jms.activemq.classic.password", "testcontainers"
        ));
    }
}
