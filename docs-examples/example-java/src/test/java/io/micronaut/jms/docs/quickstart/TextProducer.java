package io.micronaut.jms.docs.quickstart;

// tag::imports[]
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.Body;

import static io.micronaut.jms.activemq.configuration.ActiveMqConfiguration.CONNECTION_FACTORY_BEAN_NAME;
// end::imports[]
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "QuickstartSpec")
// tag::clazz[]
@JMSProducer(CONNECTION_FACTORY_BEAN_NAME) // <1>
public interface TextProducer {

    @Queue("queue_text") // <2>
    void send(@Body String body); // <3>
}
// end::clazz[]
