from typing import Annotated

# tag::imports[]
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSListener, Topic
from micronaut.messaging.annotation import MessageBody
# end::imports[]


@Requires(property="spec.name", value="DurableSubscriptionSpec")
# tag::clazz[]
@JMSListener("activeMqConnectionFactory")
class AuditListener:

    def __init__(self) -> None:
        self.audited: list[str] = []

    @Topic("orders", subscription="order-audit", durable=True)  # <1>
    def audit(self, order: Annotated[str, MessageBody]) -> None:
        self.audited.append(order)
# end::clazz[]
