package io.micronaut.jms.dev;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.dev.tck.ReloadHarness;
import io.micronaut.dev.tck.ReloadTck;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.jms.Connection;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs an application with an SQS listener through the development runtime, against LocalStack, and edits the
 * listener. A change applied in place restarts the listeners on the new listener. A restart stops the listeners of
 * the retired generation as its context stops, and the new generation consumes: with a client that holds nothing of
 * the context, the connection factory and the pool are retained; with the client micronaut-aws builds, whose
 * credentials and region providers read the environment, they are made again. Nothing of the retired generation
 * stays reachable.
 */
class SqsReloadTest {

    private static final String LISTENER = """
        package example;

        import io.micronaut.jms.annotations.JMSListener;
        import io.micronaut.jms.annotations.Queue;
        import io.micronaut.messaging.annotation.MessageBody;

        import java.util.List;
        import java.util.concurrent.CopyOnWriteArrayList;

        @JMSListener("sqsJmsConnectionFactory")
        public class Listener {
            private final List<String> received = new CopyOnWriteArrayList<>();

            @Queue("%s")
            public void receive(@MessageBody String value) {
                received.add("%s " + value);
            }

            public List<String> received() {
                return received;
            }
        }
        """;

    @TempDir
    Path project;

    @Test
    void aRestartKeepsTheConnectionFactoryAndPoolOfAClientThatHoldsNothingOfTheContext() throws Exception {
        String queue = "dev-reload-retained";
        LocalStackContainer localStack = LocalStackSqs.container();
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("micronaut.jms.sqs.enabled", "true");
        properties.put(RuntimeSqsClientFactory.ENDPOINT, localStack.getEndpoint().toString());
        properties.put("dev-reload.sqs.region", localStack.getRegion());
        properties.put("dev-reload.sqs.access-key", localStack.getAccessKey());
        properties.put("dev-reload.sqs.secret-key", localStack.getSecretKey());
        try (SqsClient client = LocalStackSqs.client();
             Connection connection = producerConnection(client, queue);
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageProducer producer = session.createProducer(session.createQueue(queue));
             ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            properties.forEach(harness::property);
            harness.source("example.Listener", LISTENER.formatted(queue, "first"));
            harness.start();

            send(session, producer, "one");
            awaitTrue("the first generation receives", () -> received(harness.context()).contains("first one"));

            // the listener class changed in place: the listeners are restarted on a new listener bean
            Object listener = listener(harness.context());
            changedInPlace(harness, "example.Listener");
            assertNotSame(listener, listener(harness.context()));
            listener = null;
            send(session, producer, "in-place");
            awaitTrue("the new listener receives", () -> received(harness.context()).contains("first in-place"));

            JMSConnectionPool pool = harness.context().getBean(JMSConnectionPool.class);
            SQSConnectionFactory factory = harness.context().getBean(SQSConnectionFactory.class);

            harness.source("example.Listener", LISTENER.formatted(queue, "second"));
            long reloadStart = System.nanoTime();
            harness.reload();
            assertEquals(2, harness.generation());

            ReloadTck.assertRetained(harness, pool);
            ReloadTck.assertRetained(harness, factory);
            assertSame(pool, harness.context().getBean(JMSConnectionPool.class));
            pool = null;
            factory = null;

            for (int i = 0; i < 3; i++) {
                String value = "two-" + i;
                send(session, producer, value);
                awaitTrue("the second generation receives " + value, () -> received(harness.context()).contains("second " + value));
            }
            System.out.println("The second generation received three messages " + Duration.ofNanos(System.nanoTime() - reloadStart).toMillis() + " ms after the reload started");
            assertFalse(received(harness.context()).stream().anyMatch(value -> value.startsWith("first")));

            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void aRestartMakesTheConnectionFactoryOfTheMicronautAwsClientAgain() throws Exception {
        String queue = "dev-reload-aws";
        LocalStackContainer localStack = LocalStackSqs.container();
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("micronaut.jms.sqs.enabled", "true");
        properties.put("aws.region", localStack.getRegion());
        properties.put("aws.access-key-id", localStack.getAccessKey());
        properties.put("aws.secret-key", localStack.getSecretKey());
        properties.put("aws.services.sqs.endpoint-override", localStack.getEndpoint().toString());
        try (SqsClient client = LocalStackSqs.client();
             Connection connection = producerConnection(client, queue);
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageProducer producer = session.createProducer(session.createQueue(queue));
             ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            properties.forEach(harness::property);
            harness.source("example.Listener", LISTENER.formatted(queue, "first"));
            harness.start();

            send(session, producer, "one");
            awaitTrue("the first generation receives", () -> received(harness.context()).contains("first one"));
            SQSConnectionFactory factory = harness.context().getBean(SQSConnectionFactory.class);

            harness.source("example.Listener", LISTENER.formatted(queue, "second"));
            harness.reload();
            assertEquals(2, harness.generation());
            assertNotSame(factory, harness.context().getBean(SQSConnectionFactory.class),
                "the providers of micronaut-aws read the environment of the stopped context: the factory is made again");
            factory = null;

            send(session, producer, "two");
            awaitTrue("the second generation receives", () -> received(harness.context()).contains("second two"));
            assertFalse(received(harness.context()).stream().anyMatch(value -> value.startsWith("first")));

            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    private static Connection producerConnection(SqsClient client, String queue) throws Exception {
        client.createQueue(CreateQueueRequest.builder().queueName(queue).build());
        Connection connection = new SQSConnectionFactory(new ProviderConfiguration(), client).createConnection();
        connection.start();
        return connection;
    }

    private static void send(Session session, MessageProducer producer, String value) throws Exception {
        producer.send(session.createTextMessage(value));
    }

    private static void changedInPlace(ReloadHarness harness, String className) {
        ApplicationContext context = harness.context();
        context.publishEvent(new ClassChangeEvent(SqsReloadTest.class, Set.of(), context.getClassLoader(),
            List.of(new ClassChange(className, ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));
    }

    private static void awaitTrue(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting until " + what);
            }
            Thread.sleep(100);
        }
    }

    private static Object listener(ApplicationContext context) {
        return context.getBean(type(context, "example.Listener"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> received(ApplicationContext context) {
        try {
            return (List<String>) type(context, "example.Listener").getMethod("received").invoke(listener(context));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot read what the listener received", e);
        }
    }

    private static Class<?> type(ApplicationContext context, String className) {
        try {
            return Class.forName(className, true, context.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(className + " is not in the application", e);
        }
    }
}
