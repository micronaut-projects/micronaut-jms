from abc import ABC, abstractmethod
from typing import Annotated

from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSProducer, Queue
from micronaut.messaging.annotation import MessageBody


@Requires(property="spec.name", value="ErrorHandlingSpec")
@JMSProducer("activeMqConnectionFactory")
class ErrorHandlingProducer(ABC):

    @Queue("error-queue")
    @abstractmethod
    def send(self, body: Annotated[str, MessageBody]) -> None:
        ...
