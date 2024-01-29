package io.micronaut.jms.docs.successhandler

// tag::imports[]
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.listener.JMSListenerSuccessHandler
import io.micronaut.messaging.annotation.MessageBody
import jakarta.inject.Singleton
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import jakarta.jms.Message
import jakarta.jms.Session

// end::imports[]

// tag::clazz[]
@JMSListener(
        CONNECTION_FACTORY_BEAN_NAME,
        successHandlers = [AccumulatingSuccessHandler::class]) // <1>
class SuccessHandlingConsumer {

    val processed: MutableList<String> = mutableListOf()

    @Queue(
            value = "success-queue",
            concurrency = "1-1",
            successHandlers = [CountingSuccessHandler::class]) // <2>
    fun receive(@MessageBody message: String) {
        processed.add(message)
    }
}
// end::clazz[]

@Singleton
class CountingSuccessHandler : JMSListenerSuccessHandler {

    val count: AtomicInteger = AtomicInteger(0)

    override fun handle(session: Session, message: Message) {
        count.incrementAndGet()
    }

}

@Singleton
class AccumulatingSuccessHandler : JMSListenerSuccessHandler {

    val messages: MutableList<Message> = Collections.synchronizedList(ArrayList())

    override fun handle(session: Session, message: Message) {
        messages.add(message)
    }

}
