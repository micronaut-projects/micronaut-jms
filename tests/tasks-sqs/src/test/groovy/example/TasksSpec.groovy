package example


import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Inject

@MicronautTest
class TasksSpec extends Specification implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    public LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack"), false)
            .withServices(LocalStackContainer.Service.SQS)

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
        localstack.start()
        return [
                "sqs-url": localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString(),
                "sqs-region": localstack.getRegion()
        ]
    }
}