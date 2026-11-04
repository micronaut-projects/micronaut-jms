package io.micronaut.jms.docs.delivery

import io.micronaut.jms.docs.AbstractJmsSpec
import spock.util.concurrent.PollingConditions

class DeliverySpec extends AbstractJmsSpec {

    void 'messages carry the delivery mode and priority of the producer method'() {
        given:
        PollingConditions polling = new PollingConditions(timeout: 20)

        when:
        AlertProducer producer = applicationContext.getBean(AlertProducer)
        producer.send('disk full')
        producer.sendWithPriority('cpu hot', 9)
        AlertListener listener = applicationContext.getBean(AlertListener)

        then:
        polling.eventually {
            listener.received.containsAll(['disk full 7 NON_PERSISTENT', 'cpu hot 9 NON_PERSISTENT'])
        }
    }
}
