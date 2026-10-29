package io.micronaut.jms.docs.delivery

import io.micronaut.jms.docs.AbstractJmsSpec
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class DeliverySpec : AbstractJmsSpec() {

    @Test
    fun testDeliveryModeAndPriority() {
        val producer = applicationContext.getBean(AlertProducer::class.java)
        producer.send("disk full")
        producer.sendWithPriority("cpu hot", 9)

        val listener = applicationContext.getBean(AlertListener::class.java)
        await().atMost(20, TimeUnit.SECONDS).until {
            listener.received.containsAll(listOf("disk full 7 NON_PERSISTENT", "cpu hot 9 NON_PERSISTENT"))
        }
    }
}
