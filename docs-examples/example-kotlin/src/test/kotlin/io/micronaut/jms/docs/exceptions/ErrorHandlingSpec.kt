package io.micronaut.jms.docs.exceptions

import io.kotest.matchers.collections.shouldHaveSize
import io.micronaut.jms.docs.AbstractJmsKotest
import kotlin.time.ExperimentalTime

@ExperimentalTime
class ErrorHandlingSpec: AbstractJmsKotest ({

    val specName = javaClass.simpleName

    given("A consumer which will throw an error") {
        val applicationContext = startContext(specName)
        `when`("the message is sent") {
            val producer = applicationContext.getBean(ErrorHandlingProducer::class.java)
            val consumer = applicationContext.getBean(ErrorThrowingConsumer::class.java)

            producer.push("throw an error")
            then("the exception is handled by the handlers") {
                consumer.processed shouldHaveSize 0
            }
        }
        applicationContext.stop()
    }
})