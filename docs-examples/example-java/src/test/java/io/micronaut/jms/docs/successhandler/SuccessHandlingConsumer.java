package io.micronaut.jms.docs.successhandler;

// tag::imports[]

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.listener.JMSListenerSuccessHandler;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
// end::imports[]

@Requires(property = "spec.name", value = "SuccessHandlingSpec")
// tag::clazz[]
@JMSListener(value = CONNECTION_FACTORY_BEAN_NAME, successHandlers = {AccumulatingSuccessHandler.class}) // <1>
class SuccessHandlingConsumer {

    Collection<String> messages = Collections.synchronizedSet(new HashSet<>());

    @Queue(value = "success-queue", concurrency = "1-1", successHandlers = {CountingSuccessHandler.class})  // <2>
    void receive(@MessageBody String message) throws JMSException {
        messages.add(message);
    }
}
// end::clazz[]

@Requires(property = "spec.name", value = "SuccessHandlingSpec")
@Singleton
class CountingSuccessHandler implements JMSListenerSuccessHandler {

    AtomicInteger count = new AtomicInteger(0);

    public void handle(Session session, Message message) {
        count.incrementAndGet();
    }

}

@Requires(property = "spec.name", value = "SuccessHandlingSpec")
@Singleton
class AccumulatingSuccessHandler implements JMSListenerSuccessHandler {

    Collection<Message> messages = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void handle(Session session, Message message) {
        messages.add(message);
    }

    @Override
    public Integer getOrder() {
        return 200;
    }
}
