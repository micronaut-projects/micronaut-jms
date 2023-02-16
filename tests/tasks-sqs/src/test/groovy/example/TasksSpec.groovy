package example

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import static example.AwsSdkV2LocalStackContainer.Service.SQS

@MicronautTest
class TasksSpec extends Specification implements TestPropertyProvider {

    @Shared
    @AutoCleanup
    AwsSdkV2LocalStackContainer localstack = new AwsSdkV2LocalStackContainer(DockerImageName.parse('localstack/localstack')).withServices(SQS)

    @Inject
    @Client('/')
    HttpClient client

    void 'should process tasks'() {
        expect:
        new PollingConditions(initialDelay: 2, timeout: 100).eventually {
            client.toBlocking().retrieve('/tasks/processed-count', Integer) > 3
        }
    }

    @Override
    Map<String, String> getProperties() {
        localstack.start()

        ['sqs-url': localstack.getEndpointOverride(SQS).toString(),
         'sqs-region': localstack.region,
         'access-key': localstack.accessKey,
         'secret-key': localstack.secretKey]
    }
}
