package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

@Singleton
public class SqsClientBuilderListener implements BeanCreatedEventListener<SqsClientBuilder> {

    @Inject
    @Property(name = "sqs-url")
    private String sqsUrl;

    @Inject
    @Property(name = "aws.region")
    private String sqsRegion;

    @Inject
    @Property(name = "aws.access-key")
    private String accessKey;

    @Inject
    @Property(name = "aws.secret-key")
    private String secretKey;

    @Override
    public SqsClientBuilder onCreated(BeanCreatedEvent<SqsClientBuilder> event) {
        SqsClientBuilder builder = event.getBean();
        builder.region(Region.of(sqsRegion));
        builder.credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            )
        );
        try {
            builder.endpointOverride(new URI(sqsUrl));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.createTestQueues(builder);
        return builder;
    }

    private void createTestQueues(SqsClientBuilder builder) {
        try (SqsClient client = builder.build()) {
            if (client.listQueues().queueUrls().stream().noneMatch(it -> it.contains(TaskConstants.FIFO_QUEUE))) {
                client.createQueue(
                    CreateQueueRequest.builder()
                        .queueName(TaskConstants.FIFO_QUEUE)
                        .attributes(
                            Map.of(
                                QueueAttributeName.FIFO_QUEUE, "true",
                                // see https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/getting-started.html
                                QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true"
                            )
                        )
                        .build()
                );
            }
        }
    }
}
