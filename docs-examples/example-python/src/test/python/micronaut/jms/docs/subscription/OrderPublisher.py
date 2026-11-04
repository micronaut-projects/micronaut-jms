from abc import ABC, abstractmethod
from typing import Annotated

from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSProducer, Topic
from micronaut.messaging.annotation import MessageBody


@Requires(property="spec.name", value="DurableSubscriptionSpec")
@JMSProducer("activeMqConnectionFactory")
class OrderPublisher(ABC):

    @Topic("orders")
    @abstractmethod
    def publish(self, order: Annotated[str, MessageBody]) -> None:
        ...
