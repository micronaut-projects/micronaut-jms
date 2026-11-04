from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.jms.docs.Await import Await
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .AuditListener import AuditListener
from .OrderPublisher import OrderPublisher


@MicronautTest
@Property(name="spec.name", value="DurableSubscriptionSpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://DurableSubscriptionSpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class DurableSubscriptionSpec:
    publisher: Annotated[OrderPublisher, Inject]
    listener: Annotated[AuditListener, Inject]

    @Test
    def test_durable_subscriber_receives_published_messages(self) -> None:
        self.publisher.publish("order-1")
        self.publisher.publish("order-2")

        listener = self.listener
        Await.until(lambda: {"order-1", "order-2"} <= set(listener.audited), timeout=20)
