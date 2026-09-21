from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.jms.docs.Await import Await
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .SelectorConsumer import SelectorConsumer
from .SelectorProducer import SelectorProducer


@MicronautTest
@Property(name="spec.name", value="SelectorSpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://SelectorSpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class SelectorSpec:
    producer: Annotated[SelectorProducer, Inject]
    consumer: Annotated[SelectorConsumer, Inject]

    @Test
    def test_queue(self) -> None:
        self.producer.send_queue("test1", True)
        self.producer.send_queue("test2", True)
        self.producer.send_queue("test3", False)

        consumer = self.consumer
        Await.until(lambda: len(consumer.message_bodies_true) == 2 and len(consumer.message_bodies_false) == 1
                    and set(consumer.message_bodies_true) == {"test1", "test2"}
                    and "test3" in consumer.message_bodies_false, timeout=20)

    @Test
    def test_topic(self) -> None:
        self.producer.send_topic("test1", True)

        consumer = self.consumer
        Await.until(lambda: len(consumer.message_bodies_topic) == 1, timeout=20)
