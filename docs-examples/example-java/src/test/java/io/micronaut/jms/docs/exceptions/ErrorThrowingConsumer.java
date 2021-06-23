package io.micronaut.jms.docs.exceptions;

import io.micronaut.context.annotation.Requires;

// tag::imports[]
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.listener.JMSListenerErrorHandler;
import io.micronaut.messaging.annotation.MessageBody;

import javax.inject.Singleton;
import javax.jms.Message;
import javax.jms.Session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
// end::imports[]

@Requires(property = "spec.name", value = "ErrorHandlingSpec")
// tag::clazz[]
@JMSListener(value = CONNECTION_FACTORY_BEAN_NAME, errorHandlers = {AccumulatingErrorHandler.class, CountingErrorHandler.class}) // <1>
class ErrorThrowingConsumer {

    Collection<String> messages = Collections.synchronizedSet(new HashSet<>());

    @Queue(value = "error-queue", concurrency = "1-1", errorHandlers = {CountingErrorHandler.class}) // <2>
    void receive(@MessageBody String message) {
        if ("throw an error".equalsIgnoreCase(message)) {
            throw new RuntimeException("this is an error"); // <3>
        }
        messages.add(message);
    }
}
// end::clazz[]

@Requires(property = "spec.name", value = "ErrorHandlingSpec")
@Singleton
class CountingErrorHandler implements JMSListenerErrorHandler {

    Integer count = 0;

    public void handle(Session session, Message message, Throwable ex) {
        count++;
    }

}

@Requires(property = "spec.name", value = "ErrorHandlingSpec")
@Singleton
class AccumulatingErrorHandler implements JMSListenerErrorHandler {

    Collection<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

    public void handle(Session session, Message message, Throwable ex) {
        if (ex != null) {
            exceptions.add(ex);
        }
    }

}
