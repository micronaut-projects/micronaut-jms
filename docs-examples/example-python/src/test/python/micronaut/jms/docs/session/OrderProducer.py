from abc import ABC, abstractmethod
from typing import Annotated

from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSProducer, Queue
from micronaut.messaging.annotation import MessageBody


@Requires(property="spec.name", value="SessionSpec")
@JMSProducer("activeMqConnectionFactory")
class OrderProducer(ABC):

    @Queue("orders")
    @abstractmethod
    def send(self, order: Annotated[str, MessageBody]) -> None:
        ...
