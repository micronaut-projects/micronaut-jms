package example;

import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;

import static io.micronaut.jms.sqs.configuration.SqsConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
public interface TasksProducer {

    @Queue(TaskConstants.FIFO_QUEUE)
    void send(@MessageBody Task body);
}
