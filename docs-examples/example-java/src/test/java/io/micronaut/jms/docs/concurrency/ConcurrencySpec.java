package io.micronaut.jms.docs.concurrency;

import io.micronaut.jms.docs.AbstractJmsSpec;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

class ConcurrencySpec extends AbstractJmsSpec {

    @Test
    void testJobsAreHandledByThreeConsumers() {
        JobProducer producer = applicationContext.getBean(JobProducer.class);
        for (int i = 0; i < 30; i++) {
            producer.submit("job-" + i);
        }

        JobListener listener = applicationContext.getBean(JobListener.class);
        await().atMost(20, SECONDS).until(() -> listener.processed.get() == 30 && listener.sessions.size() > 1);
    }
}
