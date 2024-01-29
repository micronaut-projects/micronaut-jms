package io.micronaut.jms.docs.exceptions

// tag::imports[]
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.listener.JMSListenerErrorHandler
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
        errorHandlers = [AccumulatingErrorHandler::class]) // <1>
class ErrorThrowingConsumer {

    val processed: MutableList<String> = mutableListOf()

    @Queue(
            value = "error-queue",
            concurrency = "1-1",
            errorHandlers = [CountingErrorHandler::class]) // <2>
    fun receive(@MessageBody message: String) {
        if (message == "throw an error") {
            throw RuntimeException("This is an unexpected error.") // <3>
        }
        processed.add(message)
    }
}
// end::clazz[]

@Singleton
class CountingErrorHandler : JMSListenerErrorHandler {

    val count: AtomicInteger = AtomicInteger(0)

    override fun handle(session: Session?, message: Message?, ex: Throwable?) {
        count.incrementAndGet()
    }

}

@Singleton
class AccumulatingErrorHandler : JMSListenerErrorHandler {

    val exceptions: MutableList<Throwable> = Collections.synchronizedList(ArrayList())

    override fun handle(session: Session?, message: Message?, ex: Throwable?) {
        if (ex != null) {
            exceptions.add(ex)
        }
    }

}
