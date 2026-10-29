package io.micronaut.jms.docs.session;

import io.micronaut.jms.docs.AbstractJmsSpec;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

class SessionSpec extends AbstractJmsSpec {

    @Test
    void testReplyOnTheListenerSession() {
        applicationContext.getBean(OrderProducer.class).send("A1");

        OrderListener listener = applicationContext.getBean(OrderListener.class);
        await().atMost(20, SECONDS).until(() -> listener.confirmations.contains("confirmed A1"));
    }
}
