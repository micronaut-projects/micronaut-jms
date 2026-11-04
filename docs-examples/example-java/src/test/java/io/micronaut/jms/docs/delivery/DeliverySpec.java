package io.micronaut.jms.docs.delivery;

import io.micronaut.jms.docs.AbstractJmsSpec;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

class DeliverySpec extends AbstractJmsSpec {

    @Test
    void testDeliveryModeAndPriority() {
        AlertProducer producer = applicationContext.getBean(AlertProducer.class);
        producer.send("disk full");
        producer.sendWithPriority("cpu hot", 9);

        AlertListener listener = applicationContext.getBean(AlertListener.class);
        await().atMost(20, SECONDS).until(() -> listener.received.contains("disk full 7 NON_PERSISTENT")
            && listener.received.contains("cpu hot 9 NON_PERSISTENT"));
    }
}
