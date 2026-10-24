from typing import Annotated

# tag::imports[]
from jakarta.jms import Session
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSListener, Queue
from micronaut.messaging.annotation import MessageBody
# end::imports[]


@Requires(property="spec.name", value="SessionSpec")
# tag::clazz[]
@JMSListener("activeMqConnectionFactory")
class OrderListener:

    def __init__(self) -> None:
        self.confirmations: list[str] = []

    @Queue("orders")
    def receive(self, order: Annotated[str, MessageBody], session: Session) -> None:  # <1>
        producer = session.createProducer(session.createQueue("order_confirmations"))  # <2>
        try:
            producer.send(session.createTextMessage("confirmed " + order))
        finally:
            producer.close()

    @Queue("order_confirmations")
    def confirmed(self, confirmation: Annotated[str, MessageBody]) -> None:
        self.confirmations.append(confirmation)
# end::clazz[]
