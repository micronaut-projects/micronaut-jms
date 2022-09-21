package example;


import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

@Factory
public class SqsClientFactory {

    @Bean(preDestroy = "shutdown")
    @Singleton
    AmazonSQS sqsClient(Environment environment, @Value("${sqs-url:}") String sqsUrl, @Value("${sqs-region:}") String sqsRegion) {
        if (StringUtils.isEmpty(sqsUrl)) {
            sqsUrl = "http://localhost:4566";
        }
        if (StringUtils.isEmpty(sqsRegion)) {
            sqsRegion = "eu-central-1";
        }

        AmazonSQS client = AmazonSQSClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(sqsUrl, sqsRegion))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("foo", "bar")))
                .build();
        if (client.listQueues().getQueueUrls().stream().noneMatch(it -> it.contains(TaskConstants.FIFO_QUEUE))) {
            client.createQueue(new CreateQueueRequest()
                    .withQueueName(TaskConstants.FIFO_QUEUE)
                    .addAttributesEntry("FifoQueue", "true")
                    // see https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/getting-started.html
                    .addAttributesEntry("ContentBasedDeduplication", "true")
            );
        }
        return client;

    }
}
