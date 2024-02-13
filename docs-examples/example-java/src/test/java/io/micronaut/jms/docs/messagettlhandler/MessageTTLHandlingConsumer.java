package io.micronaut.jms.docs.messagettlhandler;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Message;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Requires(property = "spec.name", value = "MessageTTLHandlingSpec")
@JMSListener(value = CONNECTION_FACTORY_BEAN_NAME)
class MessageTTLHandlingConsumer {

    Collection<jakarta.jms.Message> messages = Collections.synchronizedSet(new HashSet<>());

    @Queue(value = "message-ttl-queue")
    void receive(@Message jakarta.jms.Message message) {
        messages.add(message);
    }
}
