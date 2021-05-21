package example;

import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.messaging.annotation.MessageHeader;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
public interface TasksProducer {

    @Queue(TaskConstants.FIFO_QUEUE)
    void send(
            @MessageBody Task body,
            @MessageHeader("JMSXGroupID") String messageGroupId
    );

}
