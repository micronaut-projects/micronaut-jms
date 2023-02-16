package io.micronaut.jms.docs.successhandler;

import io.micronaut.jms.docs.AbstractJmsSpec;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

public class SuccessHandlingSpec extends AbstractJmsSpec {

    @Test
    void testCustomSuccessHandlersAtTheMethodAndClassLevel() {
        SuccessHandlingProducer producer = applicationContext.getBean(SuccessHandlingProducer.class);
        SuccessHandlingConsumer consumer = applicationContext.getBean(SuccessHandlingConsumer.class);
        CountingSuccessHandler successHandler = applicationContext.getBean(CountingSuccessHandler.class);
        AccumulatingSuccessHandler classLevelSuccessHandler = applicationContext.getBean(AccumulatingSuccessHandler.class);

        producer.send("success message no. 1");
        producer.send("success message no. 2");

        await().atMost(5, SECONDS).until(() -> consumer.messages.size() == 2 &&
                successHandler.count.get() == 2 &&
                classLevelSuccessHandler.messages.size() == 2);
    }
}
