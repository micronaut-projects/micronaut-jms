from micronaut.context.annotation import Requires
# tag::imports[]
from typing import Annotated

from micronaut.jms.annotations import JMSListener, Queue
from micronaut.messaging.annotation import MessageBody
# end::imports[]


@Requires(property="spec.name", value="QuickstartSpec")
# tag::clazz[]
@JMSListener("activeMqConnectionFactory")  # <1>
class TextConsumer:

    def __init__(self) -> None:
        self.messages: list[str] = []

    @Queue("queue_text")  # <2>
    def receive(self, body: Annotated[str, MessageBody]) -> None:  # <3>
        self.messages.append(body)
# end::clazz[]
