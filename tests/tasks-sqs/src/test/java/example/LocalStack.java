package example;

import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class LocalStack {

    private static final String IMAGE_NAME = "localstack/localstack:4.14.0";
    private static LocalStackContainer container;

    public static Map<String, String> getProperties() {
        if (container == null) {
            container = new LocalStackContainer(DockerImageName.parse(IMAGE_NAME))
                .withServices("sqs")
                .waitingFor(Wait.forHttp("/_localstack/health").forStatusCode(200));

            container.start();

            try (SqsClient sqsClient = SqsClient.builder()
                .endpointOverride(container.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(container.getAccessKey(), container.getSecretKey())
                ))
                .region(Region.of(container.getRegion()))
                .build()) {

                sqsClient.createQueue(CreateQueueRequest.builder()
                    .queueName(TaskConstants.FIFO_QUEUE)
                    .attributes(Map.of(
                        QueueAttributeName.FIFO_QUEUE, "true",
                        QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true"
                    ))
                    .build());
            }
        }

        return buildProperties(container);
    }

    private static Map<String, String> buildProperties(LocalStackContainer container) {
        Map<String, String> props = new HashMap<>();
        props.put("micronaut.jms.sqs.enabled", "true");
        props.put("aws.access-key-id", container.getAccessKey());
        props.put("aws.secret-key", container.getSecretKey());
        props.put("aws.region", container.getRegion());
        props.put("aws.services.sqs.endpoint-override", container.getEndpoint().toString());
        return props;
    }
}
