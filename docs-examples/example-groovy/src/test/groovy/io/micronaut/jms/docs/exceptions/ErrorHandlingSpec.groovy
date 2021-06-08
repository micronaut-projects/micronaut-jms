package io.micronaut.jms.docs.exceptions

import io.micronaut.jms.docs.AbstractJmsSpec

class ErrorHandlingSpec extends AbstractJmsSpec {

    void 'test custom error handlers at the method and class level'() {
        given:
        def producer = applicationContext.getBean ErrorHandlingProducer
        def consumer = applicationContext.getBean ErrorThrowingConsumer
        def errorHandler = applicationContext.getBean CountingErrorHandler
        def classLevelErrorHandler = applicationContext.getBean AccumulatingErrorHandler

        when:

        producer.send("throw an error")

        then:
        polling.eventually {
            consumer.messages.size() == 0
            errorHandler.count == 1
            classLevelErrorHandler.exceptions.size() == 1
        }
    }

}
