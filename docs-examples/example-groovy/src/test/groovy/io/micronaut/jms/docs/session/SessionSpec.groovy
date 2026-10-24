package io.micronaut.jms.docs.session

import io.micronaut.jms.docs.AbstractJmsSpec
import spock.util.concurrent.PollingConditions

class SessionSpec extends AbstractJmsSpec {

    void 'a listener replies on the session that received the message'() {
        given:
        PollingConditions polling = new PollingConditions(timeout: 20)

        when:
        applicationContext.getBean(OrderProducer).send('A1')
        OrderListener listener = applicationContext.getBean(OrderListener)

        then:
        polling.eventually {
            listener.confirmations.contains('confirmed A1')
        }
    }
}
