package io.micronaut.jms.docs.concurrency

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.MessageBody
import jakarta.jms.Session
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicInteger
// end::imports[]

@Requires(property = "spec.name", value = "ConcurrencySpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class JobListener {

    val sessions: MutableSet<Session> = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))
    val processed = AtomicInteger()

    @Queue(value = "jobs", consumers = 3) // <1>
    fun process(@MessageBody job: String, session: Session) {
        sessions.add(session) // <2>
        Thread.sleep(50)
        processed.incrementAndGet()
    }
}
// end::clazz[]
