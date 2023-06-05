package example

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.testcontainers.utility.DockerImageName
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import static example.LocalStackContainer.Service.SQS

class TasksSpec extends Specification {

    void 'should process tasks'() {
        when:
        LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse('localstack/localstack')).withServices(SQS)
        localstack.start()
        EmbeddedServer server = ApplicationContext.run(['sqs-url': localstack.getEndpointOverride(SQS).toString(),
         'sqs-region': localstack.region])
        HttpClient httpClient = server.applicationContext.createBean(HttpClient, server.URL)
        BlockingHttpClient client = httpClient.toBlocking()

        then:
        new PollingConditions(initialDelay: 2, timeout: 100).eventually {
            client.retrieve('/tasks/processed-count', Integer) > 3
        }

        cleanup:
        localstack.close()
        client.close()
        httpClient.close()
        server.close()
    }
}
