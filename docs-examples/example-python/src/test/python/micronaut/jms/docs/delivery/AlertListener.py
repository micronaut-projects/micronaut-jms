from typing import Annotated

from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSListener, Queue
from micronaut.messaging.annotation import MessageBody, MessageHeader


@Requires(property="spec.name", value="DeliverySpec")
@JMSListener("activeMqConnectionFactory")
class AlertListener:

    def __init__(self) -> None:
        self.received: list[str] = []

    @Queue("alerts")
    def receive(self, alert: Annotated[str, MessageBody],
                priority: Annotated[int, MessageHeader("JMSPriority")],
                delivery_mode: Annotated[str, MessageHeader("JMSDeliveryMode")]) -> None:
        self.received.append(f"{alert} {priority} {delivery_mode}")
