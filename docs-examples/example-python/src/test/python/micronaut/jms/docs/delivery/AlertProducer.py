from abc import ABC, abstractmethod
from typing import Annotated

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSProducer, MessagePriority, Queue
from micronaut.messaging.annotation import MessageBody
# end::imports[]


@Requires(property="spec.name", value="DeliverySpec")
# tag::clazz[]
@JMSProducer("activeMqConnectionFactory")
class AlertProducer(ABC):

    @Queue("alerts", deliveryMode="NON_PERSISTENT", priority=7)  # <1>
    @abstractmethod
    def send(self, alert: Annotated[str, MessageBody]) -> None:
        ...

    @Queue("alerts", deliveryMode="NON_PERSISTENT")
    @abstractmethod
    def send_with_priority(self, alert: Annotated[str, MessageBody],
                           priority: Annotated[int, MessagePriority]) -> None:  # <2>
        ...
# end::clazz[]
