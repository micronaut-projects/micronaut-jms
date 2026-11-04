package io.micronaut.jms.docs.producer

import io.micronaut.jms.docs.AbstractJmsSpec
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class AsyncProducerSpec : AbstractJmsSpec() {

    @Test
    fun testProducerMethodsCanReturnAFutureOrAPublisher() {
        val producer = applicationContext.getBean(ReceiptProducer::class.java)
        val listener = applicationContext.getBean(ReceiptListener::class.java)

        producer.sendAsync("receipt 1").join()
        val publisher = producer.sendReactive("receipt 2")
        Thread.sleep(500)
        assertFalse(listener.received.contains("receipt 2"), "a publisher sends once subscribed")
        complete(publisher).join()

        await().atMost(20, TimeUnit.SECONDS).until {
            listener.received.containsAll(listOf("receipt 1", "receipt 2"))
        }
    }

    private fun complete(publisher: Publisher<Void>): CompletableFuture<Void> {
        val completed = CompletableFuture<Void>()
        publisher.subscribe(object : Subscriber<Void> {
            override fun onSubscribe(subscription: Subscription) = subscription.request(1)
            override fun onNext(item: Void?) {}
            override fun onError(throwable: Throwable) {
                completed.completeExceptionally(throwable)
            }
            override fun onComplete() {
                completed.complete(null)
            }
        })
        return completed
    }
}
