# tag::imports[]
from jakarta.inject import Singleton
from micronaut.context.annotation import Factory, Requires
from micronaut.jms.activemq.classic.configuration.properties import ActiveMqClassicConfigurationProperties
from micronaut.jms.annotations import JMSConnectionFactory
from org.apache.activemq import ActiveMQXAConnectionFactory

# end::imports[]


@Requires(property="spec.name", value="CustomBrokerSpec")
# tag::clazz[]
@Factory
class ActiveMQXAConnectionBeanFactory:

    @JMSConnectionFactory("activeMQXAConnectionFactory")
    @Singleton
    def create_active_mq_xa_connection_factory(self, amq_config: ActiveMqClassicConfigurationProperties) -> ActiveMQXAConnectionFactory:
        return ActiveMQXAConnectionFactory(amq_config.getConnectionString())
# end::clazz[]
