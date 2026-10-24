package io.micronaut.jms.listener;

import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JMSTopicSubscriptionTest {

    private final List<String> calls = new ArrayList<>();
    private RuntimeException consumerFailure;
    private RuntimeException sessionCloseFailure;
    private JMSException connectionCloseFailure;

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

    @Test
    void registryClosesTheSessionAndLeavesTheDedicatedConnectionToTheCallerWhenStartingFails() {
        JMSListenerRegistry registry = new JMSListenerRegistry(Collections.emptyList(), Collections.emptyList());
        consumerFailure = new IllegalStateException("invalid selector");

        assertThrows(IllegalStateException.class, () -> registry.register(connection(), JMSDestinationType.TOPIC, "orders", false,
            Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.empty(), new JMSTopicSubscription("audit", true, false, null)));
        registry.shutdown();

        assertTrue(calls.contains("session.close[]"));
        assertFalse(calls.contains("connection.close[]"), "the processor that created the connection closes it");
    }

    @Test
    void unsupportedSharedSubscriptionsFailAsAJMSException() {
        consumerFailure = new UnsupportedOperationException("createSharedDurableConsumer(Topic, name, messageSelector) is not supported");

        JMSException e = assertThrows(JMSException.class, () -> start(new JMSTopicSubscription("audit", true, true, null), Optional.empty()));

        assertTrue(e.getMessage().contains("does not support shared subscriptions"));
        assertTrue(e.getCause() instanceof UnsupportedOperationException);
    }

    @Test
    void subscriptionOptionsAreValidated() {
        assertNull(JMSTopicSubscription.of("orders", null, false, false, null));
        assertNull(JMSTopicSubscription.of("orders", "", false, false, ""));
        assertEquals(new JMSTopicSubscription("audit", true, false, null), JMSTopicSubscription.of("orders", "audit", true, false, ""));
        assertEquals(new JMSTopicSubscription("workers", false, true, "app-1"), JMSTopicSubscription.of("orders", "workers", false, true, "app-1"));

        IllegalStateException noName = assertThrows(IllegalStateException.class, () -> JMSTopicSubscription.of("orders", null, true, false, null));
        assertTrue(noName.getMessage().contains("A subscription name is required"));
        assertThrows(IllegalStateException.class, () -> JMSTopicSubscription.of("orders", "", false, true, null));
        assertThrows(IllegalStateException.class, () -> JMSTopicSubscription.of("orders", null, false, false, "app-1"));
        IllegalStateException plain = assertThrows(IllegalStateException.class, () -> JMSTopicSubscription.of("orders", "audit", false, false, null));
        assertTrue(plain.getMessage().contains("must set durable = true or shared = true"));
    }

    @Test
    void topicListenersWithoutADurableOrSharedSubscriptionUseAPlainConsumer() throws JMSException {
        new JMSListener(session(), message -> { }, JMSDestinationType.TOPIC, "orders", null, Optional.empty()).start();
        start(new JMSTopicSubscription("plain", false, false, null), Optional.of("type = 'order'"));
        start(new JMSTopicSubscription("plain", false, false, null), Optional.empty());

        assertTrue(calls.contains("createConsumer[type = 'order']"));
        assertEquals(2, calls.stream().filter("createConsumer[]"::equals).count());
    }

    @Test
    void registryOpensADedicatedConnectionWithTheSubscriptionClientId() throws JMSException {
        JMSListenerRegistry registry = new JMSListenerRegistry(Collections.emptyList(), Collections.emptyList());
        registry.register(pool(), JMSDestinationType.TOPIC, "orders", false, Session.AUTO_ACKNOWLEDGE, message -> { },
            null, true, Optional.empty(), new JMSTopicSubscription("audit", true, false, null));

        assertEquals(List.of("factory.createConnection[]", "connection.setClientID[audit]"), calls.subList(0, 2));
        assertTrue(calls.contains("createDurableSubscriber[audit, null, false]"));

        registry.shutdown();

        assertTrue(calls.contains("connection.close[]"));
    }

    @Test
    void registryTakesAPooledConnectionWhenNoDedicatedConnectionIsNeeded() throws JMSException {
        JMSListenerRegistry registry = new JMSListenerRegistry(Collections.emptyList(), Collections.emptyList());
        registry.register(pool(), JMSDestinationType.TOPIC, "orders", false, Session.AUTO_ACKNOWLEDGE, message -> { },
            null, true, Optional.empty(), new JMSTopicSubscription("workers", false, true, null));
        registry.register(pool(), JMSDestinationType.TOPIC, "orders", false, Session.AUTO_ACKNOWLEDGE, message -> { },
            null, true, Optional.empty(), null);

        assertFalse(calls.stream().anyMatch(call -> call.startsWith("connection.setClientID")));
        assertTrue(calls.contains("createSharedConsumer[workers, null]"));
        registry.shutdown();
    }

    @Test
    void registryClosesADedicatedConnectionItOpenedWhenStartingFails() {
        JMSListenerRegistry registry = new JMSListenerRegistry(Collections.emptyList(), Collections.emptyList());
        consumerFailure = new IllegalStateException("invalid selector");
        connectionCloseFailure = new JMSException("broker gone");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> registry.register(pool(), JMSDestinationType.TOPIC,
            "orders", false, Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.empty(),
            new JMSTopicSubscription("audit", true, false, null)));

        assertTrue(calls.contains("session.close[]"));
        assertTrue(calls.contains("connection.close[]"));
        assertSame(connectionCloseFailure, e.getSuppressed()[0]);
    }

    @Test
    void aSessionCloseFailureIsSuppressedWhenStartingFails() {
        JMSListenerRegistry registry = new JMSListenerRegistry(Collections.emptyList(), Collections.emptyList());
        consumerFailure = new IllegalStateException("invalid selector");
        sessionCloseFailure = new IllegalStateException("session gone");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> registry.register(connection(), JMSDestinationType.TOPIC,
            "orders", false, Session.AUTO_ACKNOWLEDGE, message -> { }, null, true, Optional.empty(), null));

        assertSame(sessionCloseFailure, e.getSuppressed()[0]);
    }

    @Test
    void aDedicatedConnectionThatFailsToCloseOnShutdownIsSkipped() throws JMSException {
        JMSListenerRegistry registry = new JMSListenerRegistry(Collections.emptyList(), Collections.emptyList());
        registry.register(connection(), JMSDestinationType.TOPIC, "orders", false, Session.AUTO_ACKNOWLEDGE, message -> { },
            null, true, Optional.empty(), new JMSTopicSubscription("audit", true, false, null));
        connectionCloseFailure = new JMSException("broker gone");

        registry.shutdown();

        assertTrue(calls.contains("connection.close[]"));
    }

    private JMSConnectionPool pool() {
        ConnectionFactory factory = (ConnectionFactory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ConnectionFactory.class},
            (proxy, method, args) -> {
                if ("createConnection".equals(method.getName())) {
                    calls.add("factory.createConnection[]");
                    return connection();
                }
                return null;
            });
        return new JMSConnectionPool(factory, 0, 2);
    }

    private void start(JMSTopicSubscription subscription, Optional<String> selector) throws JMSException {
        new JMSListener(session(), message -> { }, JMSDestinationType.TOPIC, "orders", null, selector, subscription).start();
    }

    private Connection connection() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "createSession":
                    return session();
                case "setClientID":
                    calls.add("connection.setClientID[" + args[0] + "]");
                    return null;
                case "close":
                    calls.add("connection.close[]");
                    if (connectionCloseFailure != null) {
                        throw connectionCloseFailure;
                    }
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
                    if (consumerFailure != null) {
                        throw consumerFailure;
                    }
                    return consumer();
                case "close":
                    calls.add("session.close[]");
                    if (sessionCloseFailure != null) {
                        throw sessionCloseFailure;
                    }
                    return null;
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
