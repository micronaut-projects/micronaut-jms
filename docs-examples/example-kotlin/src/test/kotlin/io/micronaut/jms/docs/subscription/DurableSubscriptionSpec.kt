package io.micronaut.jms.docs.subscription

import io.micronaut.jms.docs.AbstractJmsSpec
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class DurableSubscriptionSpec : AbstractJmsSpec() {

    @Test
    fun durableSubscriberReceivesPublishedMessages() {
        val publisher = applicationContext.getBean(OrderPublisher::class.java)
        val listener = applicationContext.getBean(AuditListener::class.java)

        publisher.publish("order-1")
        publisher.publish("order-2")

        await().atMost(20, TimeUnit.SECONDS).until { listener.audited.containsAll(listOf("order-1", "order-2")) }
    }
}
