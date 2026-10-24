package io.micronaut.jms.docs.session;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Requires(property = "spec.name", value = "SessionSpec")
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
public interface OrderProducer {

    @Queue("orders")
    void send(@MessageBody String order);
}
