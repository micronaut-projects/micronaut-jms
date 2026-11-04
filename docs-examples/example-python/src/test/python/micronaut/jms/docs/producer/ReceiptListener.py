from typing import Annotated

from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSListener, Queue
from micronaut.messaging.annotation import MessageBody


@Requires(property="spec.name", value="AsyncProducerSpec")
@JMSListener("activeMqConnectionFactory")
class ReceiptListener:

    def __init__(self) -> None:
        self.received: list[str] = []

    @Queue("receipts")
    def receive(self, receipt: Annotated[str, MessageBody]) -> None:
        self.received.append(receipt)
