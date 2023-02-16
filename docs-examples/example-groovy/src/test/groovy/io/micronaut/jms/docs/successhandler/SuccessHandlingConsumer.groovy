package io.micronaut.jms.docs.successhandler

import io.micronaut.context.annotation.Requires
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.listener.JMSListenerSuccessHandler

// tag::imports[]

import io.micronaut.messaging.annotation.MessageBody
import jakarta.inject.Singleton

import javax.jms.Message
import javax.jms.Session
import java.util.concurrent.atomic.AtomicInteger

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME

// end::imports[]

@Requires(property = "spec.name", value = 'SuccessHandlingSpec')
// tag::clazz[]
@JMSListener(value = CONNECTION_FACTORY_BEAN_NAME, successHandlers = [AccumulatingSuccessHandler.class]) // <1>
class SuccessHandlingConsumer {

    Collection<String> messages = Collections.synchronizedSet(new HashSet<String>())

    @Queue(value = "success-queue", concurrency = "1-1", successHandlers = [CountingSuccessHandler.class]) // <2>
    void receive(@MessageBody String message) {
        messages.add(message)
    }
}
// end::clazz[]

@Requires(property = "spec.name", value = 'SuccessHandlingSpec')
@Singleton
class CountingSuccessHandler implements JMSListenerSuccessHandler {

    AtomicInteger count = new AtomicInteger(0)

    void handle(Session session, Message message) {
        count.incrementAndGet()
    }

}

@Requires(property = "spec.name", value = 'SuccessHandlingSpec')
@Singleton
class AccumulatingSuccessHandler implements JMSListenerSuccessHandler {

    Collection<Message> messages = Collections.synchronizedList(new ArrayList())

    void handle(Session session, Message message) {
        messages.add(message)
    }

}
