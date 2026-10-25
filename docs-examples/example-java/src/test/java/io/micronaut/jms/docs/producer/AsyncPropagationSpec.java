package io.micronaut.jms.docs.producer;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.jms.docs.AbstractJmsSpec;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.observation.JMSMessageObserver;
import io.micronaut.jms.observation.JMSObservation;
import jakarta.inject.Singleton;
import jakarta.jms.Message;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AsyncPropagationSpec extends AbstractJmsSpec {

    @Override
    protected Map<String, Object> getConfiguration() {
        Map<String, Object> configuration = super.getConfiguration();
        // the producer and listener of the documentation example
        configuration.put("spec.name", "AsyncProducerSpec");
        configuration.put("spec.propagation", true);
        return configuration;
    }

    @Test
    void thePropagatedContextFollowsAnAsynchronousSend() {
        ReceiptProducer producer = applicationContext.getBean(ReceiptProducer.class);
        PropagationObserver observer = applicationContext.getBean(PropagationObserver.class);

        CompletableFuture<Void> sent;
        try (PropagatedContext.Scope ignored = PropagatedContext.getOrEmpty().plus(new Caller("checkout")).propagate()) {
            sent = producer.sendAsync("receipt");
        }
        sent.join();

        assertEquals("checkout", observer.caller);
        assertNotEquals(Thread.currentThread(), observer.thread, "sent on the executor");
    }

    record Caller(String name) implements PropagatedContextElement {
    }

    @Requires(property = "spec.propagation")
    @Singleton
    static class PropagationObserver implements JMSMessageObserver {

        volatile String caller;
        volatile Thread thread;

        @Override
        public JMSObservation send(JMSDestinationType destinationType, String destination, Message message) {
            caller = PropagatedContext.getOrEmpty().find(Caller.class).map(Caller::name).orElse(null);
            thread = Thread.currentThread();
            return null;
        }
    }
}
