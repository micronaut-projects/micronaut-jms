from typing import Annotated

from jakarta.inject import Inject
from jakarta.jms import ConnectionFactory
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from micronaut.inject.qualifiers import Qualifiers
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.apache.activemq import ActiveMQConnectionFactory
from org.junit.jupiter.api import Test

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
