package io.micronaut.jms.docs.serdes;

import io.micronaut.jms.docs.AbstractJmsSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

class SerdesSpec extends AbstractJmsSpec {

    @Test
    void theSerializerAndDeserializerOfTheDestinationAreUsed() {
        applicationContext.getBean(TagProducer.class).send(List.of("jms", "oracle"));

        TagListener listener = applicationContext.getBean(TagListener.class);
        await().atMost(20, SECONDS).until(() -> listener.received.contains(List.of("jms", "oracle")));
    }
}
