package io.micronaut.jms.docs.binding

import io.micronaut.jms.docs.AbstractJmsSpec
import spock.util.concurrent.PollingConditions

class SelectorSpec extends AbstractJmsSpec {

    void 'test queue'() {
        given:
        PollingConditions polling = new PollingConditions(timeout: 20)

        when:
        SelectorProducer producer = applicationContext.getBean(SelectorProducer)
        producer.sendQueue('test1', true)
        producer.sendQueue('test2', true)
        producer.sendQueue('test3', false)

        SelectorConsumer consumer = applicationContext.getBean(SelectorConsumer)

        then:
        polling.eventually {
            consumer.messageBodiesTrue.size() == 2
            consumer.messageBodiesFalse.size() == 1
            consumer.messageBodiesTrue.containsAll(['test1', 'test2'])
            consumer.messageBodiesFalse.contains('test3')
        }
    }

    void 'test topic'() {
        given:
        PollingConditions polling = new PollingConditions(timeout: 20)

        when:
        SelectorProducer producer = applicationContext.getBean(SelectorProducer)
        producer.sendTopic('test1', true)

        SelectorConsumer consumer = applicationContext.getBean(SelectorConsumer)

        then:
        polling.eventually {
            consumer.messageBodiesTopic.size() == 1
        }
    }
}
