package io.micronaut.jms.docs.producer;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Requires(property = "spec.name", value = "AsyncProducerSpec")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class ReceiptListener {

    final List<String> received = Collections.synchronizedList(new ArrayList<>());

    @Queue("receipts")
    public void receive(@MessageBody String receipt) {
        received.add(receipt);
    }
}
