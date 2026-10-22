package io.micronaut.jms.listener;

import io.micronaut.jms.model.JMSDestinationType;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import jakarta.jms.TopicSubscriber;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JMSTopicSubscriptionTest {

    private final List<String> calls = new ArrayList<>();

    @Test
    void unsharedDurableSubscriptionUsesADurableSubscriber() throws JMSException {
        start(new JMSTopicSubscription("audit", true, false, null), Optional.of("type = 'order'"));

        assertEquals(List.of("createTopic[orders]", "createDurableSubscriber[audit, type = 'order', false]"), calls);
    }

    @Test
    void sharedDurableSubscriptionUsesASharedDurableConsumer() throws JMSException {
        start(new JMSTopicSubscription("audit", true, true, null), Optional.empty());

        assertEquals(List.of("createTopic[orders]", "createSharedDurableConsumer[audit, null]"), calls);
    }

    @Test
    void sharedSubscriptionUsesASharedConsumer() throws JMSException {
        start(new JMSTopicSubscription("workers", false, true, null), Optional.of(""));

        assertEquals(List.of("createTopic[orders]", "createSharedConsumer[workers, null]"), calls);
    }

    @Test
    void onlyUnsharedDurableSubscriptionsOrExplicitClientIdsNeedADedicatedConnection() {
        assertTrue(new JMSTopicSubscription("audit", true, false, null).requiresDedicatedConnection());
        assertEquals("audit", new JMSTopicSubscription("audit", true, false, null).effectiveClientId());
        assertFalse(new JMSTopicSubscription("audit", true, true, null).requiresDedicatedConnection());
        assertTrue(new JMSTopicSubscription("audit", true, true, "app-1").requiresDedicatedConnection());
        assertEquals("app-1", new JMSTopicSubscription("audit", true, true, "app-1").effectiveClientId());
        assertThrows(IllegalArgumentException.class, () -> new JMSTopicSubscription("", true, false, null));
    }

    @Test
    void subscriptionsAreRejectedForQueues() {
        assertThrows(IllegalArgumentException.class, () -> new JMSListener(session(), message -> { }, JMSDestinationType.QUEUE,
            "orders", null, Optional.empty(), new JMSTopicSubscription("audit", true, false, null)));
    }

    @Test
    void registryClosesADedicatedConnectionOnShutdown() throws JMSException {
        JMSListenerRegistry registry = new JMSListenerRegistry(Collections.emptyList(), Collections.emptyList());
        Connection connection = connection();
        registry.register(connection, JMSDestinationType.TOPIC, "orders", false, Session.AUTO_ACKNOWLEDGE, message -> { },
            null, true, Optional.empty(), new JMSTopicSubscription("audit", true, false, null));

        registry.shutdown();

        assertTrue(calls.contains("connection.close[]"));
    }

    private void start(JMSTopicSubscription subscription, Optional<String> selector) throws JMSException {
        new JMSListener(session(), message -> { }, JMSDestinationType.TOPIC, "orders", null, selector, subscription).start();
    }

    private Connection connection() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "createSession":
                    return session();
                case "close":
                    calls.add("connection.close[]");
                    return null;
                default:
                    return null;
            }
        });
    }

    private Session session() {
        return (Session) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Session.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "createTopic":
                    calls.add("createTopic[" + args[0] + "]");
                    return topic((String) args[0]);
                case "createDurableSubscriber", "createSharedDurableConsumer", "createSharedConsumer", "createConsumer":
                    calls.add(method.getName() + Arrays.toString(Arrays.copyOfRange(args, 1, args.length)));
                    return consumer();
                default:
                    return null;
            }
        });
    }

    private static Topic topic(String name) {
        return (Topic) Proxy.newProxyInstance(JMSTopicSubscriptionTest.class.getClassLoader(), new Class<?>[]{Topic.class},
            (proxy, method, args) -> "getTopicName".equals(method.getName()) || "toString".equals(method.getName()) ? name : null);
    }

    private static MessageConsumer consumer() {
        return (MessageConsumer) Proxy.newProxyInstance(JMSTopicSubscriptionTest.class.getClassLoader(), new Class<?>[]{TopicSubscriber.class},
            (proxy, method, args) -> null);
    }
}
