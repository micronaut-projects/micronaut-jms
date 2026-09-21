# tag::imports[]
from typing import Annotated

from jakarta.inject import Singleton
from jakarta.jms import Message, Session
from java.lang import RuntimeException, Throwable
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSListener, Queue
from micronaut.jms.listener import JMSListenerErrorHandler
from micronaut.messaging.annotation import MessageBody
# end::imports[]


@Requires(property="spec.name", value="ErrorHandlingSpec")
@Singleton
class CountingErrorHandler(JMSListenerErrorHandler):

    def __init__(self) -> None:
        self.count = 0

    def handle(self, session: Session, message: Message, ex: Throwable) -> None:
        self.count += 1


@Requires(property="spec.name", value="ErrorHandlingSpec")
@Singleton
class AccumulatingErrorHandler(JMSListenerErrorHandler):

    def __init__(self) -> None:
        self.exceptions: list[Throwable] = []

    def handle(self, session: Session, message: Message, ex: Throwable) -> None:
        if ex is not None:
            self.exceptions.append(ex)


@Requires(property="spec.name", value="ErrorHandlingSpec")
# tag::clazz[]
@JMSListener("activeMqConnectionFactory", errorHandlers=[AccumulatingErrorHandler])  # <1>
class ErrorThrowingConsumer:

    def __init__(self) -> None:
        self.messages: set[str] = set()

    @Queue("error-queue", errorHandlers=[CountingErrorHandler])  # <2>
    def receive(self, message: Annotated[str, MessageBody]) -> None:
        if message.lower() == "throw an error":
            raise RuntimeException("this is an error")  # <3>
        self.messages.add(message)
# end::clazz[]
