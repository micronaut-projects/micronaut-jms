package io.micronaut.jms.docs.session

import io.micronaut.jms.docs.AbstractJmsSpec
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class SessionSpec : AbstractJmsSpec() {

    @Test
    fun testReplyOnTheListenerSession() {
        applicationContext.getBean(OrderProducer::class.java).send("A1")

        val listener = applicationContext.getBean(OrderListener::class.java)
        await().atMost(20, TimeUnit.SECONDS).until { listener.confirmations.contains("confirmed A1") }
    }
}
