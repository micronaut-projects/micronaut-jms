from abc import ABC, abstractmethod
from typing import Annotated

# tag::imports[]
from java.util.concurrent import CompletableFuture
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSProducer, Queue
from micronaut.messaging.annotation import MessageBody
from org.reactivestreams import Publisher
# end::imports[]


@Requires(property="spec.name", value="AsyncProducerSpec")
# tag::clazz[]
@JMSProducer("activeMqConnectionFactory")
class ReceiptProducer(ABC):

    @Queue("receipts")
    @abstractmethod
    def send_async(self, receipt: Annotated[str, MessageBody]) -> CompletableFuture[None]:  # <1>
        ...

    @Queue("receipts")
    @abstractmethod
    def send_reactive(self, receipt: Annotated[str, MessageBody]) -> Publisher[None]:  # <2>
        ...
# end::clazz[]
