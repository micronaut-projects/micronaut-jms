from abc import ABC, abstractmethod
from typing import Annotated

from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSProducer, Queue
from micronaut.messaging.annotation import MessageBody


@Requires(property="spec.name", value="SuccessHandlingSpec")
@JMSProducer("activeMqConnectionFactory")
class SuccessHandlingProducer(ABC):

    @Queue("success-queue")
    @abstractmethod
    def send(self, body: Annotated[str, MessageBody]) -> None:
        ...
