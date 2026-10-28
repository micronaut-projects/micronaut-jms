from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.jms.docs.Await import Await
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .OrderListener import OrderListener
from .OrderProducer import OrderProducer


@MicronautTest
@Property(name="spec.name", value="SessionSpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://SessionSpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class SessionSpec:
    producer: Annotated[OrderProducer, Inject]
    listener: Annotated[OrderListener, Inject]

    @Test
    def test_reply_on_the_listener_session(self) -> None:
        self.producer.send("A1")

        listener = self.listener
        Await.until(lambda: "confirmed A1" in listener.confirmations, timeout=20)
