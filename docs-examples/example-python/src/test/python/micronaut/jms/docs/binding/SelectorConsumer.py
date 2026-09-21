from typing import Annotated

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSListener, Queue, Topic
from micronaut.messaging.annotation import MessageBody


@Requires(property="spec.name", value="SelectorSpec")
# tag::clazz[]
@JMSListener("activeMqConnectionFactory")
class SelectorConsumer:

    def __init__(self) -> None:
        self.message_bodies_true: list[str] = []
        self.message_bodies_false: list[str] = []
        self.message_bodies_topic: list[str] = []

    @Queue("selector_queue", messageSelector="CustomBooleanHeader=true")
    def receive(self, body: Annotated[str, MessageBody]) -> None:
        self.message_bodies_true.append(body)

    @Queue("selector_queue", messageSelector="CustomBooleanHeader=false")
    def receive2(self, body: Annotated[str, MessageBody]) -> None:
        self.message_bodies_false.append(body)

    @Topic("selector_topic", messageSelector="CustomBooleanHeader=true")
    def receive_topic(self, body: Annotated[str, MessageBody]) -> None:
        self.message_bodies_topic.append(body)
# end::clazz[]
