package io.micronaut.jms.docs.quickstart;

// tag::imports[]
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
// end::imports[]
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "QuickstartSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME) // <1>
public class TextConsumer {

    List<String> messages = Collections.synchronizedList(new ArrayList<>());

    @Queue(value = "queue_text") // <2>
    public void receive(@MessageBody String body) { // <3>
        messages.add(body);
    }
}
// end::clazz[]
