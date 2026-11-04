from typing import Annotated

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSListener, Queue
from micronaut.messaging.annotation import MessageBody
# end::imports[]


@Requires(property="spec.name", value="SerdesSpec")
# tag::clazz[]
@JMSListener("activeMqConnectionFactory")
class TagListener:

    def __init__(self) -> None:
        self.received: list[list[str]] = []

    @Queue("tags", deserializer="csv")  # <1>
    def receive(self, tags: Annotated[list[str], MessageBody]) -> None:
        self.received.append(list(tags))
# end::clazz[]
