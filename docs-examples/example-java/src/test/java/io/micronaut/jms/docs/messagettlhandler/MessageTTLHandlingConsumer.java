package io.micronaut.jms.docs.messagettlhandler;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.listener.JMSListenerSuccessHandler;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Requires(property = "spec.name", value = "MessageTTLHandlingSpec")
@JMSListener(value = CONNECTION_FACTORY_BEAN_NAME) // <1>
class MessageTTLHandlingConsumer {

    Collection<jakarta.jms.Message> messages = Collections.synchronizedSet(new HashSet<>());

    @Queue(value = "message-ttl-queue")
    void receive(@io.micronaut.jms.annotations.Message jakarta.jms.Message message) {
        messages.add(message);
    }
}
