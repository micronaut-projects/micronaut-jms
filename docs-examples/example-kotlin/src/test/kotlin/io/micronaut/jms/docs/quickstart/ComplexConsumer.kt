package io.micronaut.jms.docs.quickstart

import io.micronaut.context.annotation.Requires
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME
import io.micronaut.jms.annotations.JMSListener
import io.micronaut.jms.annotations.Topic
import io.micronaut.messaging.annotation.Body
import io.micronaut.messaging.annotation.Header
import java.util.*
import javax.annotation.Nullable
import javax.jms.Session.CLIENT_ACKNOWLEDGE
import kotlin.collections.ArrayList

@Requires(property = "spec.name", value = "QuickstartSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
class ComplexConsumer {
    val messages: MutableList<ComplexObject> = Collections.synchronizedList(ArrayList())
    val headers: MutableList<String> = Collections.synchronizedList(ArrayList())
    var nullHeaderWasNonNull: Boolean = false

    @Topic(value = "topic_complex", transacted = true, acknowledgeMode = CLIENT_ACKNOWLEDGE)
    fun onMessage( // <1>
            @Body body: ComplexObject, // <2>
            @Header("X-Custom-Header") header: String, // <3>
            @Header("X-Header-Does-Not-Exists") @Nullable nullHeader: Int? // <4>
    ) {
        if (nullHeader != null) {
            nullHeaderWasNonNull = true
        }
        headers.add(header)
        messages.add(body)
    }
}
// end::clazz[]