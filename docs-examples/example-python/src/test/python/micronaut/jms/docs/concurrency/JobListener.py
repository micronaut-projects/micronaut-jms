import threading
import time
from typing import Annotated

# tag::imports[]
from jakarta.jms import Session
from java.lang import System
from micronaut.context.annotation import Requires
from micronaut.jms.annotations import JMSListener, Queue
from micronaut.messaging.annotation import MessageBody
# end::imports[]


@Requires(property="spec.name", value="ConcurrencySpec")
# tag::clazz[]
@JMSListener("activeMqConnectionFactory")
class JobListener:

    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.session_ids: set[int] = set()
        self.processed = 0

    @Queue("jobs", consumers=3)  # <1>
    def process(self, job: Annotated[str, MessageBody], session: Session) -> None:
        with self.lock:
            self.session_ids.add(System.identityHashCode(session))  # <2>
        time.sleep(0.05)
        with self.lock:
            self.processed += 1
# end::clazz[]
