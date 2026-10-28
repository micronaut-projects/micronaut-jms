from micronaut.context.annotation import Requires
# tag::imports[]
from typing import Annotated

from jakarta.jms import Destination
from jakarta.jms import Message as JmsMessage
from java.lang import Byte, Float, Long, Short
from micronaut.jms.annotations import JMSListener, Message, Queue
from micronaut.messaging.annotation import MessageBody, MessageHeader
# end::imports[]


@Requires(property="spec.name", value="BindingSpec")
# tag::clazz[]
@JMSListener("activeMqConnectionFactory")
class MapConsumer:

    def __init__(self) -> None:
        self.message_bodies: list[dict[str, object]] = []
        self.message_headers: list[dict[str, object]] = []
        self.messages: list[JmsMessage] = []

    @Queue("queue_map")
    def receive(self, body: Annotated[dict[str, object], MessageBody],
                message: Annotated[JmsMessage, Message],
                correlation_id: Annotated[str | None, MessageHeader("JMSCorrelationID")],
                delivery_mode: Annotated[int, MessageHeader("JMSDeliveryMode")],
                destination: Annotated[Destination, MessageHeader("JMSDestination")],
                expiration: Annotated[Long, MessageHeader("JMSExpiration")],
                message_id: Annotated[str, MessageHeader("JMSMessageID")],
                priority: Annotated[int, MessageHeader("JMSPriority")],
                redelivered: Annotated[bool, MessageHeader("JMSRedelivered")],
                reply_to: Annotated[Destination | None, MessageHeader("JMSReplyTo")],
                timestamp: Annotated[Long, MessageHeader("JMSTimestamp")],
                message_type: Annotated[str | None, MessageHeader("JMSType")],
                string_header: Annotated[str | None, MessageHeader("CustomStringHeader")],
                boolean_header: Annotated[bool, MessageHeader("CustomBooleanHeader")],
                byte_header: Annotated[Byte, MessageHeader("CustomByteHeader")],
                short_header: Annotated[Short, MessageHeader("CustomShortHeader")],
                int_header: Annotated[int, MessageHeader("CustomIntegerHeader")],
                long_header: Annotated[Long, MessageHeader("CustomLongHeader")],
                float_header: Annotated[Float, MessageHeader("CustomFloatHeader")],
                double_header: Annotated[float, MessageHeader("CustomDoubleHeader")]) -> None:

        header_values = {
            "JMSCorrelationID": correlation_id,
            "JMSDeliveryMode": delivery_mode,
            "JMSDestination": destination,
            "JMSExpiration": expiration,
            "JMSMessageID": message_id,
            "JMSPriority": priority,
            "JMSRedelivered": redelivered,
            "JMSReplyTo": reply_to,
            "JMSTimestamp": timestamp,
            "JMSType": message_type,
            "CustomStringHeader": string_header,
            "CustomBooleanHeader": boolean_header,
            "CustomByteHeader": byte_header,
            "CustomShortHeader": short_header,
            "CustomIntegerHeader": int_header,
            "CustomLongHeader": long_header,
            "CustomFloatHeader": float_header,
            "CustomDoubleHeader": double_header,
        }

        self.message_headers.append(header_values)
        self.message_bodies.append(body)
        self.messages.append(message)
# end::clazz[]
