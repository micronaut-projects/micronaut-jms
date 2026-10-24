package io.micronaut.jms.docs.concurrency;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.messaging.annotation.MessageBody;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvalidConsumersSpec {

    @Test
    void noConsumersFailsAtStartup() {
        assertStartupFails("NoConsumers", "at least one consumer");
    }

    @Test
    void severalConsumersOfAPlainTopicFailAtStartup() {
        assertStartupFails("PlainTopicConsumers", "only with a shared subscription");
    }

    @Test
    void severalConsumersOfASubscriptionWithAClientIdFailAtStartup() {
        assertStartupFails("DurableTopicConsumers", "can have only one consumer");
    }

    private static void assertStartupFails(String spec, String message) {
        String broker = UUID.randomUUID().toString().replace("-", "");
        Throwable failure = assertThrows(RuntimeException.class, () -> ApplicationContext.run(Map.of(
            "micronaut.jms.activemq.classic.connectionString", "vm://" + broker + "?broker.persistent=false",
            "micronaut.jms.activemq.classic.enabled", true,
            "spec.name", spec), "test").close());
        while (failure.getCause() != null && (failure.getMessage() == null || !failure.getMessage().contains(message))) {
            failure = failure.getCause();
        }
        String text = String.valueOf(failure.getMessage());
        assertTrue(text.contains(message), text);
    }

    @Requires(property = "spec.name", value = "NoConsumers")
    @JMSListener(CONNECTION_FACTORY_BEAN_NAME)
    static class NoConsumers {
        @Queue(value = "none", consumers = 0)
        void receive(@MessageBody String body) {
        }
    }

    @Requires(property = "spec.name", value = "PlainTopicConsumers")
    @JMSListener(CONNECTION_FACTORY_BEAN_NAME)
    static class PlainTopicConsumers {
        @Topic(value = "news", consumers = 2)
        void receive(@MessageBody String body) {
        }
    }

    @Requires(property = "spec.name", value = "DurableTopicConsumers")
    @JMSListener(CONNECTION_FACTORY_BEAN_NAME)
    static class DurableTopicConsumers {
        @Topic(value = "audit", subscription = "audit-log", durable = true, shared = true, clientId = "audit-app", consumers = 2)
        void receive(@MessageBody String body) {
        }
    }
}
