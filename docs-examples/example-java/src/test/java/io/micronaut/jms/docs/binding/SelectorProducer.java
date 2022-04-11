package io.micronaut.jms.docs.binding;

// tag::imports[]

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.messaging.annotation.MessageHeader;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Requires(property = "spec.name", value = "SelectorSpec")
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
public interface SelectorProducer {

    @Queue(value = "selector_queue")
    void sendQueue(@MessageBody String body, @MessageHeader("CustomBooleanHeader") boolean booleanHeader);

    @Topic(value = "selector_topic")
    void sendTopic(@MessageBody String body, @MessageHeader("CustomBooleanHeader") boolean booleanHeader);

}
// end::clazz[]
