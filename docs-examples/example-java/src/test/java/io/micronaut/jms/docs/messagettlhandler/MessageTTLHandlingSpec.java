package io.micronaut.jms.docs.messagettlhandler;

import io.micronaut.jms.docs.AbstractJmsSpec;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageTTLHandlingSpec extends AbstractJmsSpec {

    @Test
    void testCustomSuccessHandlersAtTheMethodAndClassLevel() throws Exception {
        MessageTTLHandlingProducer producer = applicationContext.getBean(MessageTTLHandlingProducer.class);
        MessageTTLHandlingConsumer consumer = applicationContext.getBean(MessageTTLHandlingConsumer.class);

        long ttl = SECONDS.toMillis(30);
        producer.send("message with ttl", ttl);
        long expectedExpiration = System.currentTimeMillis() + ttl;

        int receiveTimeoutSeconds = 5;
        await().atMost(receiveTimeoutSeconds, SECONDS).until(() -> consumer.messages.size() == 1);

        long jmsExpiration = consumer.messages.stream().findFirst().get().getJMSExpiration();
        assertTrue(Math.abs(expectedExpiration - jmsExpiration) < SECONDS.toMillis(receiveTimeoutSeconds));
    }
}
