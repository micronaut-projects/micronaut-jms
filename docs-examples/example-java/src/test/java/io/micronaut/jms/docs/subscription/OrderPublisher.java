package io.micronaut.jms.docs.subscription;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.messaging.annotation.MessageBody;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Requires(property = "spec.name", value = "DurableSubscriptionSpec")
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
public interface OrderPublisher {

    @Topic("orders")
    void publish(@MessageBody String order);
}
