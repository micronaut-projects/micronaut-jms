package io.micronaut.jms.docs.quickstart

// tag::imports[]
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Queue
import io.micronaut.messaging.annotation.Body
// end::imports[]
import io.micronaut.context.annotation.Requires

@Requires(property = "spec.name", value = "QuickstartSpec")
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME) // <1>
interface TextProducer {

	@Queue("queue_text") // <2>
	fun send(@Body body: String) // <3>
}
// end::clazz[]
