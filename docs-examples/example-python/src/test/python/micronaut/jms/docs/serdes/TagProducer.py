from abc import ABC, abstractmethod
from typing import Annotated

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSProducer, Queue
from micronaut.messaging.annotation import MessageBody
# end::imports[]


@Requires(property="spec.name", value="SerdesSpec")
# tag::clazz[]
@JMSProducer("activeMqConnectionFactory")
class TagProducer(ABC):

    @Queue("tags", serializer="csv")  # <1>
    @abstractmethod
    def send(self, tags: Annotated[list[str], MessageBody]) -> None:
        ...
# end::clazz[]
