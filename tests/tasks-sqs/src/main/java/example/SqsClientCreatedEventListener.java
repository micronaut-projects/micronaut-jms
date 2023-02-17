package example;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Map;

@Singleton
public class SqsClientCreatedEventListener implements BeanCreatedEventListener<SqsClient> {

    @Override
    public SqsClient onCreated(BeanCreatedEvent<SqsClient> event) {
        SqsClient client = event.getBean();
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
