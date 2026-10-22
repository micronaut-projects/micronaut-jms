package io.micronaut.jms.docs.subscription;

import java.util.List;

import io.micronaut.jms.docs.AbstractJmsSpec;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

class DurableSubscriptionSpec extends AbstractJmsSpec {

    @Test
    void durableSubscriberReceivesPublishedMessages() {
        OrderPublisher publisher = applicationContext.getBean(OrderPublisher.class);
        AuditListener listener = applicationContext.getBean(AuditListener.class);

        publisher.publish("order-1");
        publisher.publish("order-2");

        await().atMost(20, SECONDS).until(() -> listener.audited.containsAll(List.of("order-1", "order-2")));
    }
}
