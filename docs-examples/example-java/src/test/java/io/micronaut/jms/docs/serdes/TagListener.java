package io.micronaut.jms.docs.serdes;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
// end::imports[]

@Requires(property = "spec.name", value = "SerdesSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class TagListener {

    final List<List<String>> received = new CopyOnWriteArrayList<>();

    @Queue(value = "tags", deserializer = "csv") // <1>
    public void receive(@MessageBody List<String> tags) {
        received.add(tags);
    }
}
// end::clazz[]
