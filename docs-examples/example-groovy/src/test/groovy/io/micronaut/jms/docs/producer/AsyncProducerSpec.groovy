package io.micronaut.jms.docs.producer

import io.micronaut.jms.docs.AbstractJmsSpec
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture

class AsyncProducerSpec extends AbstractJmsSpec {

    void 'producer methods can return a future or a publisher'() {
        given:
        PollingConditions polling = new PollingConditions(timeout: 20)
        ReceiptProducer producer = applicationContext.getBean(ReceiptProducer)
        ReceiptListener listener = applicationContext.getBean(ReceiptListener)

        when:
        producer.sendAsync('receipt 1').join()
        Publisher<Void> publisher = producer.sendReactive('receipt 2')
        sleep(500)

        then: 'a publisher sends once subscribed'
        !listener.received.contains('receipt 2')

        when:
        complete(publisher).join()

        then:
        polling.eventually {
            listener.received.containsAll(['receipt 1', 'receipt 2'])
        }
    }

    private static CompletableFuture<Void> complete(Publisher<Void> publisher) {
        CompletableFuture<Void> completed = new CompletableFuture<>()
        publisher.subscribe(new Subscriber<Void>() {
            @Override
            void onSubscribe(Subscription subscription) {
                subscription.request(1)
            }

            @Override
            void onNext(Void item) {
            }

            @Override
            void onError(Throwable throwable) {
                completed.completeExceptionally(throwable)
            }

            @Override
            void onComplete() {
                completed.complete(null)
            }
        })
        completed
    }
}
