package io.micronaut.jms.docs.concurrency

import io.micronaut.jms.docs.AbstractJmsSpec
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class ConcurrencySpec : AbstractJmsSpec() {

    @Test
    fun testJobsAreHandledByThreeConsumers() {
        val producer = applicationContext.getBean(JobProducer::class.java)
        repeat(30) { producer.submit("job-$it") }

        val listener = applicationContext.getBean(JobListener::class.java)
        await().atMost(20, TimeUnit.SECONDS).until { listener.processed.get() == 30 && listener.sessions.size > 1 }
    }
}
