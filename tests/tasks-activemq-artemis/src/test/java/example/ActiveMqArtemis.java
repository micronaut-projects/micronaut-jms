package example;

import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class ActiveMqArtemis {

    private static final String IMAGE_NAME = "apache/activemq-artemis:latest";
    private static ArtemisContainer container;

    public static Map<String, String> getProperties() {
        if (container == null) {
            container = new ArtemisContainer(IMAGE_NAME);
            container.withUser("artemis");
            container.withPassword("artemis");
            container.waitingFor(
                Wait.forLogMessage(".*AMQ221007: Server is now active.*\\n", 1)
                    .withStartupTimeout(Duration.ofSeconds(120))
            );            container.start();
            while (!container.isRunning()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }
        return getProperties(container);
    }

    private static Map<String, String> getProperties(ArtemisContainer container) {
        String brokerUrl = container.getBrokerUrl();
        Map<String, String> props = new HashMap<>();
        props.put("micronaut.jms.activemq.artemis.enabled", "true");
        props.put("micronaut.jms.activemq.artemis.connection-string", brokerUrl);
        props.put("micronaut.jms.activemq.artemis.connectionString", brokerUrl);
        props.put("micronaut.jms.activemq.artemis.username", "artemis");
        props.put("micronaut.jms.activemq.artemis.password", "artemis");
        return props;
    }
}
