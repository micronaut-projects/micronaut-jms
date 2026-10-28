from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.jms.docs.Await import Await
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .AlertListener import AlertListener
from .AlertProducer import AlertProducer


@MicronautTest
@Property(name="spec.name", value="DeliverySpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://DeliverySpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class DeliverySpec:
    producer: Annotated[AlertProducer, Inject]
    listener: Annotated[AlertListener, Inject]

    @Test
    def test_delivery_mode_and_priority(self) -> None:
        self.producer.send("disk full")
        self.producer.send_with_priority("cpu hot", 9)

        listener = self.listener
        Await.until(lambda: {"disk full 7 NON_PERSISTENT", "cpu hot 9 NON_PERSISTENT"} <= set(listener.received), timeout=20)
