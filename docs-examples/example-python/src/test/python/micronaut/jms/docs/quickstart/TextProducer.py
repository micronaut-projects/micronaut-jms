from micronaut.context.annotation import Requires
# tag::imports[]
from abc import ABC, abstractmethod
from typing import Annotated

from micronaut.jms.annotations import JMSProducer, Queue
from micronaut.messaging.annotation import MessageBody
# end::imports[]


@Requires(property="spec.name", value="QuickstartSpec")
# tag::clazz[]
@JMSProducer("activeMqConnectionFactory")  # <1>
class TextProducer(ABC):

    @Queue("queue_text")  # <2>
    @abstractmethod
    def send(self, body: Annotated[str, MessageBody]) -> None:  # <3>
        ...
# end::clazz[]
