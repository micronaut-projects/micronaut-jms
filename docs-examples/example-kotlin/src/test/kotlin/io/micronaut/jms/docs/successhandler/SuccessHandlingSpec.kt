package io.micronaut.jms.docs.successhandler

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micronaut.jms.docs.AbstractJmsKotest
import org.awaitility.Awaitility
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime

@ExperimentalTime
class SuccessHandlingSpec: AbstractJmsKotest ({

    val specName = javaClass.simpleName

    given("A consumer registers success handlers") {
        val applicationContext = startContext(specName)
        `when`("the message is sent") {
            val producer = applicationContext.getBean(SuccessHandlingProducer::class.java)
            val consumer = applicationContext.getBean(SuccessHandlingConsumer::class.java)
            val errorHandler = applicationContext.getBean(CountingSuccessHandler::class.java)
            val classLevelErrorHandler = applicationContext.getBean(AccumulatingSuccessHandler::class.java)

            producer.push("success message no 1")
            producer.push("success message no 2")

            then("the success flow is processed by the handlers") {
                Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
                    consumer.processed shouldHaveSize 2
                    errorHandler.count.get() shouldBe 2
                    classLevelErrorHandler.messages shouldHaveSize 2
                    true
                }
            }
        }
        applicationContext.stop()
    }
})
