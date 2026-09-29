package io.micronaut.jms.docs.binding

import io.micronaut.jms.docs.AbstractJmsSpec
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class SelectorSpec : AbstractJmsSpec() {

    @Test
    fun testQueue() {
        val producer = applicationContext.getBean(SelectorProducer::class.java)
        producer.sendQueue("test1", true)
        producer.sendQueue("test2", true)
        producer.sendQueue("test3", false)

        val consumer = applicationContext.getBean(SelectorConsumer::class.java)
        await().atMost(20, TimeUnit.SECONDS).until {
            consumer.messageBodiesTrue.size == 2 && consumer.messageBodiesFalse.size == 1 &&
                consumer.messageBodiesTrue.containsAll(listOf("test1", "test2")) &&
                consumer.messageBodiesFalse.contains("test3")
        }
    }

    @Test
    fun testTopic() {
        val producer = applicationContext.getBean(SelectorProducer::class.java)
        producer.sendTopic("test1", true)

        val consumer = applicationContext.getBean(SelectorConsumer::class.java)
        await().atMost(20, TimeUnit.SECONDS).until { consumer.messageBodiesTopic.size == 1 }
    }
}
