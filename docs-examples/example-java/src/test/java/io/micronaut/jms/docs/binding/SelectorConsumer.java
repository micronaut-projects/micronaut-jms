package io.micronaut.jms.docs.binding;

// tag::imports[]

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.messaging.annotation.Body;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Requires(property = "spec.name", value = "SelectorSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class SelectorConsumer {

    List<String> messageBodiesTrue = Collections.synchronizedList(new ArrayList<>());

    List<String> messageBodiesFalse = Collections.synchronizedList(new ArrayList<>());

    List<String> messageBodiesTopic = Collections.synchronizedList(new ArrayList<>());

    @Queue(value = "selector_queue", concurrency = "1-5", messageSelector = "CustomBooleanHeader=true")
    public void receive(@Body String body) {
        messageBodiesTrue.add(body);
    }

    @Queue(value = "selector_queue", concurrency = "1-5", messageSelector = "CustomBooleanHeader=false")
    public void receive2(@Body String body) {
        messageBodiesFalse.add(body);
    }

    @Topic(value = "selector_topic", messageSelector = "CustomBooleanHeader=true")
    public void receiveTopic(@Body String body) {
        messageBodiesTopic.add(body);
    }
}
// end::clazz[]
