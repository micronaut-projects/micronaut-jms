package io.micronaut.jms.docs.exceptions;

import io.micronaut.jms.docs.AbstractJmsSpec;
import io.micronaut.jms.docs.quickstart.TextConsumer;
import io.micronaut.jms.docs.quickstart.TextProducer;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

public class ErrorHandlingSpec extends AbstractJmsSpec {

    @Test
    void testCustomErrorHandlersAtTheMethodAndClassLevel() {
        ErrorHandlingProducer producer = applicationContext.getBean(ErrorHandlingProducer.class);
        ErrorThrowingConsumer consumer = applicationContext.getBean(ErrorThrowingConsumer.class);
        CountingErrorHandler errorHandler = applicationContext.getBean(CountingErrorHandler.class);
        AccumulatingErrorHandler classLevelErrorHandler = applicationContext.getBean(AccumulatingErrorHandler.class);

        producer.send("throw an error");

        await().atMost(3, SECONDS).until(() -> consumer.messages.size() == 0 &&
                errorHandler.count == 1 &&
                classLevelErrorHandler.exceptions.size() == 1);
    }
}
