package io.micronaut.jms.docs.quickstart

import io.micronaut.jms.docs.AbstractJmsSpec
import org.junit.jupiter.api.Test

import org.awaitility.Awaitility.await
import java.util.concurrent.TimeUnit

class QuickstartSpec : AbstractJmsSpec() {

    @Test
    fun testTextProducerAndConsumer() {
// tag::producer[]
val textProducer = applicationContext.getBean(TextProducer::class.java)
textProducer.send("quickstart")
// end::producer[]

        val textConsumer = applicationContext.getBean(TextConsumer::class.java)
        await().atMost(3, TimeUnit.SECONDS).until {
            textConsumer.messages.size == 1 &&
                textConsumer.messages[0] == "quickstart"
        }
    }
}
