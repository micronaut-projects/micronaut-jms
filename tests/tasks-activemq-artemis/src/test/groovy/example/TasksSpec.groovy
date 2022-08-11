package example

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.testcontainers.containers.GenericContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@MicronautTest
class TasksSpec extends Specification implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    public static GenericContainer<?> activeMQContainer = new GenericContainer<>("makyo/activemq-artemis:latest")
            .withEnv([
                    "ARTEMIS_USERNAME": "artemis",
                    "ARTEMIS_PASSWORD": "artemis"
            ])
            .withExposedPorts(61616)
    @Inject
    @Client("/")
    HttpClient client

    PollingConditions pollingConditions = new PollingConditions(initialDelay: 2, timeout: 100)

    def 'should process tasks'() {
        expect:
            pollingConditions.eventually {
                client.toBlocking().retrieve("/tasks/processed-count", Integer) > 3
            }
    }

    @Override
    Map<String, String> getProperties() {
        activeMQContainer.start()
        return [
                "micronaut.jms.activemq.artemis.enabled": "true",
                "micronaut.jms.activemq.artemis.connection-string": "tcp://${activeMQContainer.getHost()}:${activeMQContainer.getMappedPort(61616)}",
                "micronaut.jms.activemq.artemis.username": "artemis",
                "micronaut.jms.activemq.artemis.password": "artemis"
        ]
    }
}

