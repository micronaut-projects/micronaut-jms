from typing import Annotated

from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.jms.docs.Await import Await
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .JobListener import JobListener
from .JobProducer import JobProducer


@MicronautTest
@Property(name="spec.name", value="ConcurrencySpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://ConcurrencySpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class ConcurrencySpec:
    producer: Annotated[JobProducer, Inject]
    listener: Annotated[JobListener, Inject]

    @Test
    def test_jobs_are_handled_by_three_consumers(self) -> None:
        for i in range(30):
            self.producer.submit(f"job-{i}")

        listener = self.listener
        Await.until(lambda: listener.processed == 30 and len(listener.session_ids) > 1, timeout=20)
