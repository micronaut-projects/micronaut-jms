from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.jms.docs.Await import Await
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .TagListener import TagListener
from .TagProducer import TagProducer


@MicronautTest
@Property(name="spec.name", value="SerdesSpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://SerdesSpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class SerdesSpec:
    producer: Annotated[TagProducer, Inject]
    listener: Annotated[TagListener, Inject]

    @Test
    def test_the_serializer_and_deserializer_of_the_destination_are_used(self) -> None:
        self.producer.send(["jms", "oracle"])

        listener = self.listener
        Await.until(lambda: ["jms", "oracle"] in listener.received, timeout=20)
