package io.micronaut.jms.docs.messagettlhandler;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.MessageTTL;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Requires(property = "spec.name", value = "MessageTTLHandlingSpec")
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
public interface MessageTTLHandlingProducer {
    @Queue("message-ttl-queue")
    void send(@MessageBody String body, @MessageTTL long messageTTL);
}
