package io.micronaut.jms.docs.producer

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
import org.reactivestreams.Publisher
import java.util.concurrent.CompletableFuture
// end::imports[]

@Requires(property = "spec.name", value = "AsyncProducerSpec")
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface ReceiptProducer {

    @Queue("receipts")
    fun sendAsync(@MessageBody receipt: String): CompletableFuture<Void> // <1>

    @Queue("receipts")
    fun sendReactive(@MessageBody receipt: String): Publisher<Void> // <2>
}
// end::clazz[]
