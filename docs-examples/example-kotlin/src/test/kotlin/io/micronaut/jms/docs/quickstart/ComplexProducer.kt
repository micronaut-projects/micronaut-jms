package io.micronaut.jms.docs.quickstart

// tag::imports[]
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSProducer
import io.micronaut.jms.annotations.Topic
import io.micronaut.messaging.annotation.Body
import io.micronaut.messaging.annotation.Header
import javax.jms.Session.CLIENT_ACKNOWLEDGE

@Requires(property = "spec.name", value = "QuickstartSpec")
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
interface ComplexProducer {
    @Topic(value = "topic_complex", transacted = true, acknowledgeMode = CLIENT_ACKNOWLEDGE) // <1>
    fun post(
            @Body message: ComplexObject, // <2>
            @Header("X-Custom-Header") header: String) // <3>
}

data class ComplexObject(
        @JsonProperty("str") val str: String,
        @JsonProperty("int") val int: Int,
        @JsonProperty("map") val map: Map<String, String>)
// end::clazz[]