package io.micronaut.jms.docs.serdes

import io.micronaut.jms.docs.AbstractJmsSpec
import spock.util.concurrent.PollingConditions

class SerdesSpec extends AbstractJmsSpec {

    void 'the serializer and deserializer of the destination are used'() {
        when:
        applicationContext.getBean(TagProducer).send(['jms', 'oracle'])
        TagListener listener = applicationContext.getBean(TagListener)

        then:
        new PollingConditions(timeout: 20).eventually {
            listener.received.contains(['jms', 'oracle'])
        }
    }
}
