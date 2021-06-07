package io.micronaut.jms.docs.exceptions

import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Queue
import io.micronaut.jms.listener.JMSListenerErrorHandler
import io.micronaut.messaging.annotation.MessageBody
import java.util.*
import javax.inject.Singleton
import javax.jms.Message
import javax.jms.Session
import kotlin.collections.ArrayList

@JMSListener(
        CONNECTION_FACTORY_BEAN_NAME,
        errorHandlers = [
            AccumulatingErrorHandler::class,
            CountingErrorHandler::class])
class ErrorThrowingConsumer {

    val processed: MutableList<String> = mutableListOf()

    @Queue(
            value = "error-queue",
            concurrency = "1-1",
            errorHandlers = [CountingErrorHandler::class])
    fun receive(@MessageBody message: String) {
        if (message == "throw an error") {
            throw RuntimeException("This is an unexpected error.")
        }
        processed.add(message)
    }
}

@Singleton
class CountingErrorHandler : JMSListenerErrorHandler {

    var count: Int = 0

    override fun handle(session: Session?, message: Message?, ex: Throwable?) {
        count++
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
