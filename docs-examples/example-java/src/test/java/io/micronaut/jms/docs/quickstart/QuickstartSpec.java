package io.micronaut.jms.docs.quickstart;

import io.micronaut.jms.docs.AbstractJmsSpec;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

class QuickstartSpec extends AbstractJmsSpec {

    @Test
    void testTextProducerAndConsumer() {
// tag::producer[]
TextProducer textProducer = applicationContext.getBean(TextProducer.class);
textProducer.send("quickstart");
// end::producer[]

        TextConsumer textConsumer = applicationContext.getBean(TextConsumer.class);
        await().atMost(3, SECONDS).until(() ->
            textConsumer.messages.size() == 1 &&
                textConsumer.messages.get(0).equals("quickstart")
        );
    }
}
