from micronaut.context.annotation import Requires
# tag::imports[]
from abc import ABC, abstractmethod
from typing import Annotated

from java.lang import Byte, Float, Long, Short
from micronaut.jms.annotations import JMSProducer, Queue
from micronaut.messaging.annotation import MessageBody, MessageHeader
# end::imports[]


@Requires(property="spec.name", value="BindingSpec")
# tag::clazz[]
@JMSProducer("activeMqConnectionFactory")
class MapProducer(ABC):

    @Queue("queue_map")
    @abstractmethod
    def send(self, body: Annotated[dict[str, object], MessageBody],
             correlation_id: Annotated[str | None, MessageHeader("JMSCorrelationID")],
             string_header: Annotated[str | None, MessageHeader("CustomStringHeader")],
             boolean_header: Annotated[bool, MessageHeader("CustomBooleanHeader")],
             byte_header: Annotated[Byte, MessageHeader("CustomByteHeader")],
             short_header: Annotated[Short, MessageHeader("CustomShortHeader")],
             int_header: Annotated[int, MessageHeader("CustomIntegerHeader")],
             long_header: Annotated[Long, MessageHeader("CustomLongHeader")],
             float_header: Annotated[Float, MessageHeader("CustomFloatHeader")],
             double_header: Annotated[float, MessageHeader("CustomDoubleHeader")]) -> None:
        ...
# end::clazz[]
