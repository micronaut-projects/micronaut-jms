package io.micronaut.jms.docs.binding;

import java.util.Arrays;

import io.micronaut.jms.docs.AbstractJmsSpec;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

class SelectorSpec extends AbstractJmsSpec {

    @Test
    void testQueue() {

        SelectorProducer producer = applicationContext.getBean(SelectorProducer.class);
        producer.sendQueue("test1", true);
        producer.sendQueue("test2", true);
        producer.sendQueue("test3", false);

        SelectorConsumer consumer = applicationContext.getBean(SelectorConsumer.class);
        await().atMost(20, SECONDS)
               .until(() -> consumer.messageBodiesTrue.size() == 2 && consumer.messageBodiesFalse.size() == 1
                       && consumer.messageBodiesTrue.containsAll(Arrays.asList("test1", "test2"))
                        && consumer.messageBodiesFalse.contains("test3"));
    }

    @Test
    void testTopic() {

        SelectorProducer producer = applicationContext.getBean(SelectorProducer.class);
        producer.sendTopic("test1", true);

        SelectorConsumer consumer = applicationContext.getBean(SelectorConsumer.class);
        await().atMost(20, SECONDS)
               .until(() -> consumer.messageBodiesTopic.size() == 1);
    }
}
