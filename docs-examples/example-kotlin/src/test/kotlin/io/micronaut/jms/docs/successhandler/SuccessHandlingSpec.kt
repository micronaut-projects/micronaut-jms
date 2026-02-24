package io.micronaut.jms.docs.successhandler

import io.micronaut.jms.docs.AbstractJmsSpec
import org.junit.jupiter.api.Test

import org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

class SuccessHandlingSpec : AbstractJmsSpec() {

    @Test
    fun testCustomSuccessHandlersAtTheMethodAndClassLevel() {
        val producer = applicationContext.getBean(SuccessHandlingProducer::class.java)
        val consumer = applicationContext.getBean(SuccessHandlingConsumer::class.java)
        val successHandler = applicationContext.getBean(CountingSuccessHandler::class.java)
        val classLevelSuccessHandler = applicationContext.getBean(AccumulatingSuccessHandler::class.java)

        producer.push("success message no. 1")
        producer.push("success message no. 2")

        await().atMost(5, TimeUnit.SECONDS).until {
            consumer.processed.size == 2 &&
                successHandler.count.get() == 2 &&
                classLevelSuccessHandler.messages.size == 2
        }
    }
}
