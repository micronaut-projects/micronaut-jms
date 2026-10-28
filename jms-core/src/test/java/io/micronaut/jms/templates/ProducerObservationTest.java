package io.micronaut.jms.templates;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.observation.JMSMessageObserver;
import io.micronaut.jms.observation.JMSObservation;
import io.micronaut.jms.observation.ObservationStubs.RecordingObserver;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.messaging.exceptions.MessageListenerException;
import io.micronaut.messaging.exceptions.MessagingClientException;
import io.micronaut.messaging.exceptions.MessagingSystemException;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.micronaut.jms.observation.ObservationStubs.message;
import static io.micronaut.jms.observation.ObservationStubs.stub;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProducerObservationTest {

    private ApplicationContext context;
    private StubBroker broker;
    private RecordingObserver observer;

    @BeforeEach
    void start() {
        context = ApplicationContext.run(Map.of("spec.name", "ProducerObservationTest"));
        broker = context.getBean(StubBroker.class);
        observer = context.getBean(RecordingObserver.class);
    }

    @AfterEach
    void stop() {
        context.close();
    }

    @Test
    void producerMethodsAreObservedByTheObserverBeans() {
        OrderProducer producer = context.getBean(OrderProducer.class);

        producer.send("first");
        producer.send("second");
        assertThrows(MessagingClientException.class, () -> producer.sendFailing("third"));

        assertEquals(List.of("orders first", "orders second"), broker.sent);
        assertEquals(List.of(
            "recording send QUEUE orders",
            "recording send QUEUE orders before send",
            "recording send QUEUE orders completed",
            "recording send QUEUE orders",
            "recording send QUEUE orders before send",
            "recording send QUEUE orders completed",
            "recording send QUEUE failing",
            "recording send QUEUE failing before send",
            "recording send QUEUE failing completed with JMSException"), observer.events);
    }

    @Test
    void aSendThatFailsToConnectIsObservedAsAFailure() {
        OrderProducer producer = context.getBean(OrderProducer.class);
        broker.connectionFails = true;

        MessagingClientException failure = assertThrows(MessagingClientException.class, () -> producer.send("first"));

        JmsProducer<Object> template = new JmsProducer<>(JMSDestinationType.QUEUE, context.getBean(JMSConnectionPool.class),
            context.getBean(DefaultSerializerDeserializer.class));
        template.addObservers(List.of(observer));
        jakarta.jms.Queue unnamed = stub(jakarta.jms.Queue.class, (name, args) -> {
            throw new JMSException("no name");
        });
        assertThrows(MessagingClientException.class, () -> template.send("prices", "up", 0L));
        assertThrows(MessagingClientException.class, () -> template.send(unnamed, message("down", null, null), 0L));

        assertEquals(List.of(), broker.sent);
        assertInstanceOf(MessagingSystemException.class, failure.getCause());
        assertEquals(List.of(
            "recording send QUEUE orders",
            "recording send QUEUE orders completed with MessagingSystemException",
            "recording send QUEUE prices",
            "recording send QUEUE prices completed with MessagingSystemException",
            "recording send QUEUE Queue stub",
            "recording send QUEUE Queue stub completed with MessagingSystemException"), observer.events);
    }

    @Test
    void errorsOfTheSendAndOfObserversCompleteTheObservationsAndAreRethrown() {
        AssertionError observerFailure = new AssertionError("before send failed");
        JMSMessageObserver failing = new JMSMessageObserver() {
            @Override
            public JMSObservation send(JMSDestinationType destinationType, String destination) {
                return new JMSObservation() {
                    @Override
                    public void beforeSend(Message message) {
                        throw observerFailure;
                    }

                    @Override
                    public void complete(@Nullable Throwable error) {
                        // nothing to complete
                    }
                };
            }

            @Override
            public int getOrder() {
                return 1;
            }
        };
        JmsProducer<Object> failingObserver = new JmsProducer<>(JMSDestinationType.QUEUE, context.getBean(JMSConnectionPool.class),
            context.getBean(DefaultSerializerDeserializer.class));
        failingObserver.addObservers(List.of(observer, failing));
        JmsProducer<Object> failingSend = new JmsProducer<>(JMSDestinationType.QUEUE, context.getBean(JMSConnectionPool.class),
            context.getBean(DefaultSerializerDeserializer.class));
        failingSend.addObservers(List.of(observer));

        assertSame(observerFailure, assertThrows(AssertionError.class,
            () -> failingObserver.send("orders", message("a", null, null), 0L)));
        broker.sendError = new AssertionError("send failed");
        assertSame(broker.sendError, assertThrows(AssertionError.class,
            () -> failingSend.send("orders", message("b", null, null), 0L)));

        assertEquals(List.of(), broker.sent);
        assertEquals(List.of(
            "recording send QUEUE orders",
            "recording send QUEUE orders before send",
            "recording send QUEUE orders completed with AssertionError",
            "recording send QUEUE orders",
            "recording send QUEUE orders before send",
            "recording send QUEUE orders completed with AssertionError"), observer.events);
    }

    @Test
    void theProducerNamesTheDestinationOfTheObservationAndCommitsTransactedSends() {
        JmsProducer<Object> producer = new JmsProducer<>(JMSDestinationType.TOPIC, context.getBean(JMSConnectionPool.class),
            context.getBean(DefaultSerializerDeserializer.class), true, Session.SESSION_TRANSACTED);
        producer.addObservers(List.of(observer));
        Topic topic = stub(Topic.class, (name, args) -> "prices");
        Destination other = stub(Destination.class, (name, args) -> null);

        producer.send("prices", message("up", topic, null), 0L);
        producer.send(other, message("down", other, null), 0L);

        assertEquals(List.of("prices up", "Destination stub down"), broker.sent);
        assertEquals(2, broker.commits.size());
        assertEquals(List.of(
            "recording send TOPIC prices",
            "recording send TOPIC prices before send",
            "recording send TOPIC prices completed",
            "recording send TOPIC Destination stub",
            "recording send TOPIC Destination stub before send",
            "recording send TOPIC Destination stub completed"), observer.events);
    }

    @Test
    void failedTransactedSendsAreRolledBack() {
        JmsProducer<Object> producer = new JmsProducer<>(JMSDestinationType.QUEUE, context.getBean(JMSConnectionPool.class),
            context.getBean(DefaultSerializerDeserializer.class), true, Session.SESSION_TRANSACTED);
        producer.addObservers(List.of(observer));

        assertThrows(MessagingClientException.class, () -> producer.send("failing", message("a", null, null), 0L));
        broker.rollbackFails = true;
        MessagingClientException failedRollback = assertThrows(MessagingClientException.class,
            () -> producer.send("failing", message("b", null, null), 0L));

        assertEquals(List.of("rollback"), broker.rollbacks);
        assertInstanceOf(MessageListenerException.class, failedRollback.getCause());
        assertEquals(List.of(
            "recording send QUEUE failing",
            "recording send QUEUE failing before send",
            "recording send QUEUE failing completed with JMSException",
            "recording send QUEUE failing",
            "recording send QUEUE failing before send",
            "recording send QUEUE failing completed with JMSException"), observer.events);
    }

    @JMSProducer("stub")
    @Requires(property = "spec.name", value = "ProducerObservationTest")
    interface OrderProducer {

        @Queue("orders")
        void send(@MessageBody String body);

        @Queue("failing")
        void sendFailing(@MessageBody String body);
    }

    /**
     * One connection and session, whose producers record the messages they send and fail to send to {@code failing}.
     */
    @Singleton
    @Requires(property = "spec.name", value = "ProducerObservationTest")
    static class StubBroker {

        final List<String> sent = new CopyOnWriteArrayList<>();
        final List<String> commits = new CopyOnWriteArrayList<>();
        final List<String> rollbacks = new CopyOnWriteArrayList<>();
        volatile boolean rollbackFails;
        volatile boolean connectionFails;
        volatile @Nullable AssertionError sendError;
        final ConnectionFactory connectionFactory;

        StubBroker() {
            Session session = stub(Session.class, (name, args) -> switch (name) {
                case "createQueue" -> stub(jakarta.jms.Queue.class, (n, a) -> args[0]);
                case "createTopic" -> stub(Topic.class, (n, a) -> args[0]);
                case "createProducer" -> producer((Destination) args[0]);
                case "createTextMessage" -> message((String) args[0], null, null);
                case "commit" -> commits.add("commit");
                case "rollback" -> {
                    if (rollbackFails) {
                        throw new JMSException("rollback failed");
                    }
                    yield rollbacks.add("rollback");
                }
                default -> null;
            });
            Connection connection = stub(Connection.class, (name, args) -> "createSession".equals(name) ? session : null);
            connectionFactory = stub(ConnectionFactory.class, (name, args) -> {
                if (!"createConnection".equals(name)) {
                    return null;
                }
                if (connectionFails) {
                    throw new JMSException("broker down");
                }
                return connection;
            });
        }

        private MessageProducer producer(Destination destination) throws JMSException {
            String name = destination instanceof jakarta.jms.Queue queue ? queue.getQueueName()
                : destination instanceof Topic topic ? topic.getTopicName() : destination.toString();
            return stub(MessageProducer.class, (method, args) -> {
                if ("send".equals(method)) {
                    if (sendError != null) {
                        throw sendError;
                    }
                    if ("failing".equals(name)) {
                        throw new JMSException("broker unavailable");
                    }
                    sent.add(name + " " + ((jakarta.jms.TextMessage) args[0]).getText());
                }
                return null;
            });
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "ProducerObservationTest")
    static class StubBeans {

        @Singleton
        @Named("stub")
        JMSConnectionPool stubPool(StubBroker broker) {
            return new JMSConnectionPool(broker.connectionFactory, 0, 5);
        }

        @Singleton
        RecordingObserver recordingObserver() {
            return new RecordingObserver("recording", 0);
        }
    }
}
