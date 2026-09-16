from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.jms.docs.Await import Await
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .ErrorHandlingProducer import ErrorHandlingProducer
from .ErrorThrowingConsumer import AccumulatingErrorHandler, CountingErrorHandler, ErrorThrowingConsumer


@MicronautTest
@Property(name="spec.name", value="ErrorHandlingSpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://ErrorHandlingSpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class ErrorHandlingSpec:
    producer: Annotated[ErrorHandlingProducer, Inject]
    consumer: Annotated[ErrorThrowingConsumer, Inject]
    error_handler: Annotated[CountingErrorHandler, Inject]
    class_level_error_handler: Annotated[AccumulatingErrorHandler, Inject]

    @Test
    def test_custom_error_handlers_at_the_method_and_class_level(self) -> None:
        self.producer.send("throw an error")

        Await.until(lambda: len(self.consumer.messages) == 0
                    and self.error_handler.count == 1
                    and len(self.class_level_error_handler.exceptions) == 1)
