from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Singleton
from jakarta.jms import ConnectionFactory
from micronaut.context.event import BeanCreatedEvent, BeanCreatedEventListener
from org.apache.activemq import ActiveMQConnectionFactory
# end::imports[]


@Requires(property="spec.name", value="CustomizeBrokerSpec")
# tag::clazz[]
@Singleton
class CustomizeBrokerJMSConnectionPoolListener(BeanCreatedEventListener[ConnectionFactory]):

    def onCreated(self, event: BeanCreatedEvent[ConnectionFactory]) -> ConnectionFactory:
        connection_factory = event.getBean()
        if isinstance(connection_factory, ActiveMQConnectionFactory):
            connection_factory.setUseAsyncSend(True)
        return connection_factory
# end::clazz[]
