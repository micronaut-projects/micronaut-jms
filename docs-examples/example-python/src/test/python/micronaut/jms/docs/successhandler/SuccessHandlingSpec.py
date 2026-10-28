from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.jms.docs.Await import Await
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .SuccessHandlingConsumer import AccumulatingSuccessHandler, CountingSuccessHandler, SuccessHandlingConsumer
from .SuccessHandlingProducer import SuccessHandlingProducer


@MicronautTest
@Property(name="spec.name", value="SuccessHandlingSpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://SuccessHandlingSpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class SuccessHandlingSpec:
    producer: Annotated[SuccessHandlingProducer, Inject]
    consumer: Annotated[SuccessHandlingConsumer, Inject]
    success_handler: Annotated[CountingSuccessHandler, Inject]
    class_level_success_handler: Annotated[AccumulatingSuccessHandler, Inject]

    @Test
    def test_custom_success_handlers_at_the_method_and_class_level(self) -> None:
        self.producer.send("success message no. 1")
        self.producer.send("success message no. 2")

        Await.until(lambda: len(self.consumer.messages) == 2
                    and self.success_handler.count == 2
                    and len(self.class_level_success_handler.messages) == 2)
