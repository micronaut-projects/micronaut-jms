from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.annotation import EachBean, Factory, Primary
from micronaut.jms.configuration.properties import JMSConfigurationProperties
from micronaut.jms.pool import JMSConnectionPool
from org.apache.activemq import ActiveMQXAConnectionFactory

# end::imports[]


@Requires(property="spec.name", value="CustomBrokerSpec")
# tag::clazz[]
@Factory
class JmsConnectionPoolActiveMQXAConnectionFactory:

    def __init__(self, properties: JMSConfigurationProperties):
        self.properties = properties

    @EachBean(ActiveMQXAConnectionFactory)
    @Singleton
    @Primary
    def create_jms_connection_pool(self, connection_factory: ActiveMQXAConnectionFactory) -> JMSConnectionPool:
        return JMSConnectionPool(connection_factory, self.properties.getInitialPoolSize(), self.properties.getMaxPoolSize())
# end::clazz[]
