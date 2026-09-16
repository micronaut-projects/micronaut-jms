package io.micronaut.jms.docs.binding

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.annotations.Topic
import io.micronaut.messaging.annotation.MessageBody

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
// end::imports[]

@Requires(property = 'spec.name', value = 'SelectorSpec')
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class SelectorConsumer {

    List<String> messageBodiesTrue = [].asSynchronized()

    List<String> messageBodiesFalse = [].asSynchronized()

    List<String> messageBodiesTopic = [].asSynchronized()

    @Queue(value = 'selector_queue', messageSelector = 'CustomBooleanHeader=true')
    void receive(@MessageBody String body) {
        messageBodiesTrue << body
    }

    @Queue(value = 'selector_queue', messageSelector = 'CustomBooleanHeader=false')
    void receive2(@MessageBody String body) {
        messageBodiesFalse << body
    }

    @Topic(value = 'selector_topic', messageSelector = 'CustomBooleanHeader=true')
    void receiveTopic(@MessageBody String body) {
        messageBodiesTopic << body
    }
}
// end::clazz[]
