package io.micronaut.jms.dev;

import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.ArrayList;

/**
 * One LocalStack SQS for the tests of the module.
 */
final class LocalStackSqs {

    private static LocalStackContainer container;

    private LocalStackSqs() {
    }

    static synchronized LocalStackContainer container() {
        if (container == null) {
            container = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
                .withServices(LocalStackContainer.Service.SQS)
                .waitingFor(Wait.forHttp("/_localstack/health").forStatusCode(200));
            // SQS needs no Docker socket, which LocalStack mounts for other services and which Podman cannot mount
            container.setBinds(new ArrayList<>());
            container.start();
        }
        return container;
    }

    /**
     * @return A client of the test, outside any generation of the application
     */
    static SqsClient client() {
        LocalStackContainer localStack = container();
        return SqsClient.builder()
            .endpointOverride(localStack.getEndpoint())
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
            .region(Region.of(localStack.getRegion()))
            .build();
    }
}
