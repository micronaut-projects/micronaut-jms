package example;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

@Factory
public class SqsClientFactory {

    @Primary
    @Singleton
    SqsClient sqsClient(
        Environment environment,
        @Value("${sqs-url:}") String sqsUrl,
        @Value("${sqs-region:}") String sqsRegion
    ) throws URISyntaxException {
        if (StringUtils.isEmpty(sqsUrl)) {
            sqsUrl = "http://localhost:4566";
        }
        if (StringUtils.isEmpty(sqsRegion)) {
            sqsRegion = "eu-central-1";
        }

        SqsClient client = SqsClient.builder()
            .region(Region.of(sqsRegion))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("foo", "bar")
                )
            )
            .endpointOverride(new URI(sqsUrl))
            .build();

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
        return client;
    }
}
