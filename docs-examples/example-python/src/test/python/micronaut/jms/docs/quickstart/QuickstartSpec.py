from typing import Annotated

from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from micronaut.jms.docs.Await import Await
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .TextConsumer import TextConsumer
from .TextProducer import TextProducer


@MicronautTest
@Property(name="spec.name", value="QuickstartSpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://QuickstartSpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class QuickstartSpec:
    application_context: Annotated[ApplicationContext, Inject]
    text_consumer: Annotated[TextConsumer, Inject]

    @Test
    def test_text_producer_and_consumer(self) -> None:
# tag::producer[]
        text_producer = self.application_context.getBean(TextProducer)
        text_producer.send("quickstart")
# end::producer[]

        Await.until(lambda: self.text_consumer.messages == ["quickstart"])
