from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from micronaut.inject.qualifiers import Qualifiers
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.activemq import ActiveMQConnectionFactory
from org.junit.jupiter.api import Test

# TODO(python): java.type needed because the imported jakarta.jms.ConnectionFactory interface is not a Java class
# at runtime (getBeansOfType(ConnectionFactory, ...) fails with "Unsupported operation identifier 'typeHashCode'")
ConnectionFactory = java.type("jakarta.jms.ConnectionFactory")


@MicronautTest
@Property(name="spec.name", value="CustomizeBrokerSpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://CustomizeBrokerSpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class CustomizeBrokerSpec:
    application_context: Annotated[ApplicationContext, Inject]

    @Test
    def test_customize_broker(self) -> None:
        connection_factories = self.application_context.getBeansOfType(
            ConnectionFactory,
            Qualifiers.byName("activeMqConnectionFactory"))

        amq_connection_factories = [cf for cf in connection_factories if isinstance(cf, ActiveMQConnectionFactory)]
        assert len(amq_connection_factories) > 0

        amqcf = amq_connection_factories[0]
        assert amqcf.isUseAsyncSend()
