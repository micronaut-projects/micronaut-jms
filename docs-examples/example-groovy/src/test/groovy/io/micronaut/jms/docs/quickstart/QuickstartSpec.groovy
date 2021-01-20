package io.micronaut.jms.docs.quickstart

import io.micronaut.jms.docs.AbstractJmsSpec

class QuickstartSpec extends AbstractJmsSpec {

    void 'text producer and consumer'() {
        when:
// tag::producer[]
def textProducer = applicationContext.getBean(TextProducer)
textProducer.send 'quickstart'
// end::producer[]

        def textConsumer = applicationContext.getBean(TextConsumer)

        then:
        polling.eventually {
            textConsumer.messages.size() == 1
            textConsumer.messages[0] == 'quickstart'
        }
    }
}
