import time
from typing import Annotated

from jakarta.inject import Inject
from java.util.concurrent import CompletableFuture
from micronaut.context.annotation import Property
from micronaut.jms.docs.Await import Await
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from org.reactivestreams import Publisher, Subscriber, Subscription

from .ReceiptListener import ReceiptListener
from .ReceiptProducer import ReceiptProducer


class CompletionSubscriber(Subscriber):

    def __init__(self) -> None:
        self.completed: CompletableFuture = CompletableFuture()

    def onSubscribe(self, subscription: Subscription) -> None:
        subscription.request(1)

    def onNext(self, item: object) -> None:
        pass

    def onError(self, throwable: Exception) -> None:
        self.completed.completeExceptionally(throwable)

    def onComplete(self) -> None:
        self.completed.complete(None)


@MicronautTest
@Property(name="spec.name", value="AsyncProducerSpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://AsyncProducerSpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class AsyncProducerSpec:
    producer: Annotated[ReceiptProducer, Inject]
    listener: Annotated[ReceiptListener, Inject]

    @Test
    def test_producer_methods_can_return_a_future_or_a_publisher(self) -> None:
        self.producer.send_async("receipt 1").join()
        publisher: Publisher = self.producer.send_reactive("receipt 2")
        time.sleep(0.5)
        assert "receipt 2" not in self.listener.received, "a publisher sends once subscribed"
        subscriber = CompletionSubscriber()
        publisher.subscribe(subscriber)
        subscriber.completed.join()

        listener = self.listener
        Await.until(lambda: {"receipt 1", "receipt 2"} <= set(listener.received), timeout=20)
