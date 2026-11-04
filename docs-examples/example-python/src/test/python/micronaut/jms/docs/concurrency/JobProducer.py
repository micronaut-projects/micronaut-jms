from abc import ABC, abstractmethod
from typing import Annotated

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSProducer, Queue
from micronaut.messaging.annotation import MessageBody
# end::imports[]


@Requires(property="spec.name", value="ConcurrencySpec")
# tag::clazz[]
@JMSProducer("activeMqConnectionFactory")
class JobProducer(ABC):

    @Queue("jobs", transacted=True)  # <1>
    @abstractmethod
    def submit(self, job: Annotated[str, MessageBody]) -> None:
        ...
# end::clazz[]
