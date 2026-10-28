# tag::imports[]
from typing import Annotated

from jakarta.inject import Singleton
from jakarta.jms import Message, Session
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSListener, Queue
from micronaut.jms.listener import JMSListenerSuccessHandler
from micronaut.messaging.annotation import MessageBody
# end::imports[]


@Requires(property="spec.name", value="SuccessHandlingSpec")
@Singleton
class CountingSuccessHandler(JMSListenerSuccessHandler):

    def __init__(self) -> None:
        self.count = 0

    def handle(self, session: Session, message: Message) -> None:
        self.count += 1


@Requires(property="spec.name", value="SuccessHandlingSpec")
@Singleton
class AccumulatingSuccessHandler(JMSListenerSuccessHandler):

    def __init__(self) -> None:
        self.messages: list[Message] = []

    def handle(self, session: Session, message: Message) -> None:
        self.messages.append(message)

    def getOrder(self) -> int:
        return 200


@Requires(property="spec.name", value="SuccessHandlingSpec")
# tag::clazz[]
@JMSListener("activeMqConnectionFactory", successHandlers=[AccumulatingSuccessHandler])  # <1>
class SuccessHandlingConsumer:

    def __init__(self) -> None:
        self.messages: set[str] = set()

    @Queue("success-queue", successHandlers=[CountingSuccessHandler])  # <2>
    def receive(self, message: Annotated[str, MessageBody]) -> None:
        self.messages.add(message)
# end::clazz[]
