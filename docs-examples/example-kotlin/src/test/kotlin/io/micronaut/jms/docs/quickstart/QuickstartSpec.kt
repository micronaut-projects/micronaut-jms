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

		`when`("the message is published") {
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

		applicationContext.stop()
	}
})
