package io.micronaut.jms.docs.successhandler

import io.micronaut.jms.docs.AbstractJmsSpec

class SuccessHandlingSpec extends AbstractJmsSpec {

    void 'test custom success handlers at the class and method level'() {
        given:
        def producer = applicationContext.getBean SuccessHandlingProducer
        def consumer = applicationContext.getBean SuccessHandlingConsumer
        def errorHandler = applicationContext.getBean CountingSuccessHandler
        def classLevelSuccessHandler = applicationContext.getBean AccumulatingSuccessHandler

        when:
        producer.send("success message no. 1")
        producer.send("success message no. 2")

        then:
        polling.eventually {
            consumer.messages.size() == 2
            errorHandler.count.get() == 2
            classLevelSuccessHandler.messages.size() == 2
        }
    }

}
