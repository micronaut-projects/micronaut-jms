package io.micronaut.jms.docs.subscription

import io.micronaut.jms.docs.AbstractJmsSpec
import spock.util.concurrent.PollingConditions

class DurableSubscriptionSpec extends AbstractJmsSpec {

    void 'durable subscriber receives published messages'() {
        given:
        PollingConditions polling = new PollingConditions(timeout: 20)
        OrderPublisher publisher = applicationContext.getBean(OrderPublisher)
        AuditListener listener = applicationContext.getBean(AuditListener)

        when:
        publisher.publish('order-1')
        publisher.publish('order-2')

        then:
        polling.eventually {
            listener.audited.containsAll(['order-1', 'order-2'])
        }
    }
}
