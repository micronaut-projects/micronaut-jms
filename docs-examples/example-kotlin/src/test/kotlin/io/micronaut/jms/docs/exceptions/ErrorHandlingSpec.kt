package io.micronaut.jms.docs.exceptions

import io.micronaut.jms.docs.AbstractJmsSpec
import org.junit.jupiter.api.Test

import org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

class ErrorHandlingSpec : AbstractJmsSpec() {

    @Test
    fun testCustomErrorHandlersAtTheMethodAndClassLevel() {
        val producer = applicationContext.getBean(ErrorHandlingProducer::class.java)
        val consumer = applicationContext.getBean(ErrorThrowingConsumer::class.java)
        val errorHandler = applicationContext.getBean(CountingErrorHandler::class.java)
        val classLevelErrorHandler = applicationContext.getBean(AccumulatingErrorHandler::class.java)

        producer.push("throw an error")

        await().atMost(5, TimeUnit.SECONDS).until {
            consumer.processed.size == 0 &&
                errorHandler.count.get() == 1 &&
                classLevelErrorHandler.exceptions.size == 1
        }
    }
}
