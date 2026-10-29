package io.micronaut.jms.docs.serdes

import io.micronaut.jms.docs.AbstractJmsSpec
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class SerdesSpec : AbstractJmsSpec() {

    @Test
    fun testTheSerializerAndDeserializerOfTheDestinationAreUsed() {
        applicationContext.getBean(TagProducer::class.java).send(listOf("jms", "oracle"))

        val listener = applicationContext.getBean(TagListener::class.java)
        await().atMost(20, TimeUnit.SECONDS).until { listener.received.contains(listOf("jms", "oracle")) }
    }
}
