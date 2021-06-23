package io.micronaut.jms.docs.quickstart

import io.kotest.assertions.timing.eventually
import io.kotest.matchers.shouldBe
import io.micronaut.jms.docs.AbstractJmsKotest
import org.opentest4j.AssertionFailedError
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
class QuickstartSpec : AbstractJmsKotest({

    val specName = javaClass.simpleName

    given("A basic producer and consumer") {
        val applicationContext = startContext(specName)

		`when`("a text message is published") {
			val textConsumer = applicationContext.getBean(TextConsumer::class.java)
// tag::producer[]
val textProducer = applicationContext.getBean(TextProducer::class.java)
textProducer.send("quickstart")
// end::producer[]

            then("the message is consumed") {
                eventually(3.seconds, AssertionFailedError::class) {
                    textConsumer.messages.size shouldBe 1
                    textConsumer.messages[0] shouldBe "quickstart"
                }
            }
		}

		`when`("an object message is published") {
			val complexConsumer = applicationContext.getBean(ComplexConsumer::class.java)
			val complexProducer = applicationContext.getBean(ComplexProducer::class.java)
			complexProducer.post(ComplexObject("string", 1, mapOf(
					"field1" to "value1",
					"field2" to "value2")), "header")

			then("the object message is consumed") {
				eventually(3.seconds, AssertionFailedError::class) {
					complexConsumer.messages.size shouldBe 1
					complexConsumer.messages[0] shouldBe ComplexObject("string", 1, mapOf(
							"field1" to "value1",
							"field2" to "value2"))
					complexConsumer.headers.size shouldBe 1
					complexConsumer.headers[0] shouldBe "header"
					complexConsumer.nullHeaderWasNonNull shouldBe false
					// TODO: add transaction check using custom success handler
					// TODO: add acknowledgement check using custom success handler
				}
			}
		}
		applicationContext.stop()
	}
})
