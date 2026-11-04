package io.micronaut.jms.docs.producer;

import io.micronaut.jms.docs.AbstractJmsSpec;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncProducerSpec extends AbstractJmsSpec {

    @Test
    void producerMethodsCanReturnAFutureOrAPublisher() throws InterruptedException {
        ReceiptProducer producer = applicationContext.getBean(ReceiptProducer.class);
        ReceiptListener listener = applicationContext.getBean(ReceiptListener.class);

        producer.sendAsync("receipt 1").join();
        Publisher<Void> publisher = producer.sendReactive("receipt 2");
        Thread.sleep(500);
        assertTrue(listener.received.stream().noneMatch("receipt 2"::equals), "a publisher sends once subscribed");
        complete(publisher).join();

        await().atMost(20, SECONDS).until(() -> listener.received.containsAll(List.of("receipt 1", "receipt 2")));
    }

    private static CompletableFuture<Void> complete(Publisher<Void> publisher) {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(Void item) {
            }

            @Override
            public void onError(Throwable throwable) {
                completed.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                completed.complete(null);
            }
        });
        return completed;
    }
}
