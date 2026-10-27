package io.micronaut.jms.configuration;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.MessageDeliveryMode;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.observation.JMSMessageObserver;
import io.micronaut.jms.observation.JMSObservation;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.messaging.annotation.MessageHeader;
import io.micronaut.messaging.exceptions.MessagingClientException;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncProducerMethodTest {

    private ApplicationContext context;

    @AfterEach
    void stop() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void aPublisherSubscribedOnAnotherThreadSendsWithTheCallersContext() throws Exception {
        start(Map.of());
        ReceiptProducer producer = context.getBean(ReceiptProducer.class);
        CallerObserver observer = context.getBean(CallerObserver.class);

        CompletableFuture<Void> sent;
        Publisher<Void> publisher;
        try (PropagatedContext.Scope ignored = PropagatedContext.getOrEmpty().plus(new Caller("checkout")).propagate()) {
            sent = producer.sendAsync("receipt");
            publisher = producer.sendReactive("receipt");
        }
        sent.get(5, TimeUnit.SECONDS);
        Signal signal = new Signal();
        Thread subscriber = new Thread(() -> publisher.subscribe(signal));
        subscriber.start();
        signal.await();

        assertNull(signal.error);
        assertEquals(List.of("checkout", "checkout"), observer.callers);
        assertEquals(2, context.getBean(StubBroker.class).sent.get());
    }

    @Test
    void invalidArgumentsCompleteTheFutureExceptionally() {
        start(Map.of());
        ReceiptProducer producer = context.getBean(ReceiptProducer.class);

        CompletableFuture<Void> sent = producer.sendAsyncWithMode("receipt", "SOMETIMES");

        ExecutionException failure = assertThrows(ExecutionException.class, () -> sent.get(5, TimeUnit.SECONDS));
        assertInstanceOf(MessagingClientException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("expected NON_PERSISTENT or PERSISTENT"), failure.getCause().getMessage());
        assertEquals(0, context.getBean(StubBroker.class).sent.get());
    }

    @Test
    void invalidArgumentsAreSignalledByThePublisher() throws InterruptedException {
        start(Map.of());
        ReceiptProducer producer = context.getBean(ReceiptProducer.class);

        Publisher<Void> publisher = producer.sendReactiveWithMode("receipt", "SOMETIMES");
        Signal signal = new Signal();
        publisher.subscribe(signal);
        signal.await();

        assertInstanceOf(MessagingClientException.class, signal.error);
        assertTrue(signal.error.getMessage().contains("expected NON_PERSISTENT or PERSISTENT"), signal.error.getMessage());
        assertEquals(0, context.getBean(StubBroker.class).sent.get());
    }

    @Test
    void producerSessionsAreBoundedByConfiguration() throws Exception {
        start(Map.of("micronaut.jms.producer-sessions.max-active", 1, "micronaut.jms.producer-sessions.max-wait", "5s"));
        ReceiptProducer producer = context.getBean(ReceiptProducer.class);
        StubBroker broker = context.getBean(StubBroker.class);

        List<CompletableFuture<Void>> sends = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 20; i++) {
            sends.add(producer.sendAsync("receipt"));
        }
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

        assertEquals(20, broker.sent.get());
        assertEquals(1, broker.maxOpenSessions.get(), "the sends wait for the one session");
    }

    @Test
    void theConfigurationHasDefaults() {
        start(Map.of());
        JMSProducerSessionConfigurationValues values = new JMSProducerSessionConfigurationValues(
            context.getBean(io.micronaut.jms.configuration.properties.JMSProducerSessionConfiguration.class));

        assertEquals(new JMSProducerSessionConfigurationValues(true, 8, 32, Duration.ofSeconds(10)), values);
    }

    @Test
    void sessionsAreNotReusedWhenDisabled() {
        start(Map.of("micronaut.jms.producer-sessions.enabled", false));
        ReceiptProducer producer = context.getBean(ReceiptProducer.class);

        producer.send("receipt", "order-1");
        producer.send("receipt", "order-1");

        assertEquals(2, context.getBean(StubBroker.class).sessions.get());
    }

    private void start(Map<String, Object> configuration) {
        Map<String, Object> properties = new HashMap<>(configuration);
        properties.put("spec.name", "AsyncProducerMethodTest");
        context = ApplicationContext.run(properties);
    }

    record JMSProducerSessionConfigurationValues(boolean enabled, int maxIdle, int maxActive, Duration maxWait) {
        JMSProducerSessionConfigurationValues(io.micronaut.jms.configuration.properties.JMSProducerSessionConfiguration configuration) {
            this(configuration.isEnabled(), configuration.getMaxIdle(), configuration.getMaxActive(), configuration.getMaxWait());
        }
    }

    record Caller(String name) implements PropagatedContextElement {
    }

    static final class Signal implements Subscriber<Void> {

        final CountDownLatch done = new CountDownLatch(1);
        volatile Throwable error;

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(Void unused) {
            // publishers of sends emit no items
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            done.countDown();
        }

        @Override
        public void onComplete() {
            done.countDown();
        }

        void await() throws InterruptedException {
            assertTrue(done.await(5, TimeUnit.SECONDS), "the publisher completed");
        }
    }

    @JMSProducer("stub")
    @Requires(property = "spec.name", value = "AsyncProducerMethodTest")
    interface ReceiptProducer {

        @Queue("receipts")
        void send(@MessageBody String body, @MessageHeader("order") String order);

        @Queue("receipts")
        CompletableFuture<Void> sendAsync(@MessageBody String body);

        @Queue("receipts")
        Publisher<Void> sendReactive(@MessageBody String body);

        @Queue("receipts")
        CompletableFuture<Void> sendAsyncWithMode(@MessageBody String body, @MessageDeliveryMode String mode);

        @Queue("receipts")
        Publisher<Void> sendReactiveWithMode(@MessageBody String body, @MessageDeliveryMode String mode);
    }

    @Singleton
    @Requires(property = "spec.name", value = "AsyncProducerMethodTest")
    static class CallerObserver implements JMSMessageObserver {

        final List<String> callers = new CopyOnWriteArrayList<>();

        @Override
        public JMSObservation send(JMSDestinationType destinationType, String destination, Message message) {
            callers.add(PropagatedContext.getOrEmpty().find(Caller.class).map(Caller::name).orElse("none"));
            return null;
        }
    }

    /**
     * Counts the sessions it creates, the most that are open at once and the messages sent.
     */
    @Singleton
    @Requires(property = "spec.name", value = "AsyncProducerMethodTest")
    static class StubBroker {

        final AtomicInteger sent = new AtomicInteger();
        final AtomicInteger sessions = new AtomicInteger();
        final AtomicInteger openSessions = new AtomicInteger();
        final AtomicInteger maxOpenSessions = new AtomicInteger();
        final ConnectionFactory connectionFactory;

        StubBroker() {
            jakarta.jms.Queue queue = stub(jakarta.jms.Queue.class, (name, args) -> null);
            MessageProducer producer = stub(MessageProducer.class, (name, args) -> {
                if ("send".equals(name)) {
                    sent.incrementAndGet();
                }
                return null;
            });
            Connection connection = stub(Connection.class, (name, args) -> {
                if (!"createSession".equals(name)) {
                    return null;
                }
                sessions.incrementAndGet();
                maxOpenSessions.accumulateAndGet(openSessions.incrementAndGet(), Math::max);
                return stub(Session.class, (sessionMethod, sessionArgs) -> switch (sessionMethod) {
                    case "createQueue" -> queue;
                    case "createProducer" -> producer;
                    case "createTextMessage" -> stub(TextMessage.class, (messageMethod, messageArgs) -> null);
                    case "close" -> {
                        openSessions.decrementAndGet();
                        yield null;
                    }
                    default -> null;
                });
            });
            connectionFactory = stub(ConnectionFactory.class, (name, args) -> "createConnection".equals(name) ? connection : null);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "AsyncProducerMethodTest")
    static class StubPoolFactory {

        @Singleton
        @Named("stub")
        JMSConnectionPool stubPool(StubBroker broker) {
            return new JMSConnectionPool(broker.connectionFactory, 0, 5);
        }
    }

    private static <T> T stub(Class<T> type, Answer answer) {
        Object stub = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object result = switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> type.getSimpleName() + " stub";
                default -> answer.answer(method.getName(), args == null ? new Object[0] : args);
            };
            if (result == null && method.getReturnType().isPrimitive()) {
                return defaultValue(method.getReturnType());
            }
            return result;
        });
        return type.cast(stub);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }

    @FunctionalInterface
    private interface Answer {
        Object answer(String method, Object[] args) throws JMSException;
    }
}
