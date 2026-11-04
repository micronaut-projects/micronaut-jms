package io.micronaut.jms.docs.producer;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
// end::imports[]

@Requires(property = "spec.name", value = "AsyncProducerSpec")
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
public interface ReceiptProducer {

    @Queue("receipts")
    CompletableFuture<Void> sendAsync(@MessageBody String receipt); // <1>

    @Queue("receipts")
    Publisher<Void> sendReactive(@MessageBody String receipt); // <2>
}
// end::clazz[]
