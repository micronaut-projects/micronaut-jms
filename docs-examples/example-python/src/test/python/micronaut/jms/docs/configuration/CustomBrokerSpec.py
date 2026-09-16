from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from micronaut.inject.qualifiers import Qualifiers
from micronaut.jms.pool import JMSConnectionPool, PooledConnection
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

# TODO(python): java.type needed because the imported jakarta.jms.XAConnection interface is not a Java class at
# runtime (isinstance(connection, XAConnection) is always False, java.instanceof rejects it as "not a Java class")
XAConnection = java.type("jakarta.jms.XAConnection")


@MicronautTest
@Property(name="spec.name", value="CustomBrokerSpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://CustomBrokerSpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class CustomBrokerSpec:
    application_context: Annotated[ApplicationContext, Inject]

    @Test
    def test_custom_broker(self) -> None:
        connection_pool = self.application_context.getBean(
            JMSConnectionPool,
            Qualifiers.byName("activeMQXAConnectionFactory"))

        connection = connection_pool.createConnection()
        assert isinstance(connection, PooledConnection)

        real_connection = connection.get()
        assert isinstance(real_connection, XAConnection)
