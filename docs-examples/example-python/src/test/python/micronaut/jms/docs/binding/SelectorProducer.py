from abc import ABC, abstractmethod
from typing import Annotated

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSProducer, Queue, Topic
from micronaut.messaging.annotation import MessageBody, MessageHeader


@Requires(property="spec.name", value="SelectorSpec")
# tag::clazz[]
@JMSProducer("activeMqConnectionFactory")
class SelectorProducer(ABC):

    @Queue("selector_queue")
    @abstractmethod
    def send_queue(self, body: Annotated[str, MessageBody],
                   boolean_header: Annotated[bool, MessageHeader("CustomBooleanHeader")]) -> None:
        ...

    @Topic("selector_topic")
    @abstractmethod
    def send_topic(self, body: Annotated[str, MessageBody],
                   boolean_header: Annotated[bool, MessageHeader("CustomBooleanHeader")]) -> None:
        ...
# end::clazz[]
