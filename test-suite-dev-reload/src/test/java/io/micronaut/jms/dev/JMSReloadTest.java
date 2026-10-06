/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jms.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.dev.tck.ReloadHarness;
import io.micronaut.dev.tck.ReloadTck;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
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
 * Runs an application with a JMS listener through the development runtime, against an ActiveMQ Artemis broker, and
 * edits the listener. A change applied in place restarts the listeners on the new listener; a restart stops the
 * listeners of the retired generation as its context stops, keeps the connection factory and the connection pool,
 * on whose connections the new generation listens, and nothing of the retired generation stays reachable. A change
 * under {@code micronaut.jms} releases the pool.
 */
class JMSReloadTest {

    private static final String QUEUE = "dev-reload-queue";

    private static final String LISTENER = """
        package example;

        import io.micronaut.jms.annotations.JMSListener;
        import io.micronaut.jms.annotations.Queue;
        import io.micronaut.messaging.annotation.MessageBody;

        import java.util.List;
        import java.util.concurrent.CopyOnWriteArrayList;

        @JMSListener("activeMqArtemisConnectionFactory")
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
    void anInPlaceChangeRestartsTheListenersAndARestartKeepsThePoolAndLeavesTheRetiredGenerationCollectable() throws Exception {
        Map<String, String> properties = Artemis.properties();
        try (Connection connection = connectionFactory(properties).createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageProducer producer = session.createProducer(session.createQueue(QUEUE));
             ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            properties.forEach(harness::property);
            harness.source("example.Listener", LISTENER.formatted(QUEUE, "first"));
            harness.start();
            assertReloaderPresent(harness.context());

            send(session, producer, "one");
            awaitTrue("the first generation receives", () -> received(harness.context()).contains("first one"));
            ReloadTck.assertFollowsReload(harness, JMSReloadTest::listener);

            // the listener class changed in place: the listeners are restarted on a new listener bean
            Object listener = listener(harness.context());
            long inPlaceStart = System.nanoTime();
            changedInPlace(harness, "example.Listener");
            Object recreated = listener(harness.context());
            assertNotSame(listener, recreated);
            for (int i = 0; i < 4; i++) {
                String value = "in-place-" + i;
                send(session, producer, value);
                awaitTrue("the new listener receives " + value, () -> received(harness.context()).contains("first " + value));
            }
            System.out.println("The restarted listeners received a message " + millisSince(inPlaceStart) + " ms after the change");
            listener = null;
            recreated = null;

            JMSConnectionPool pool = harness.context().getBean(JMSConnectionPool.class);
            ConnectionFactory factory = harness.context().getBean(ActiveMQJMSConnectionFactory.class);

            harness.source("example.Listener", LISTENER.formatted(QUEUE, "second"));
            long reloadStart = System.nanoTime();
            harness.reload();
            assertEquals(2, harness.generation());
            assertReloaderPresent(harness.context());

            // the connection factory and the pool, with its connections, are kept
            ReloadTck.assertRetained(harness, pool);
            ReloadTck.assertRetained(harness, factory);
            assertSame(pool, harness.context().getBean(JMSConnectionPool.class));

            // the retired context stopped its listener as it stopped: every message reaches the second generation
            for (int i = 0; i < 4; i++) {
                String value = "two-" + i;
                send(session, producer, value);
                awaitTrue("the second generation receives " + value, () -> received(harness.context()).contains("second " + value));
            }
            long restarted = millisSince(reloadStart);
            System.out.println("The second generation received four messages " + restarted + " ms after the reload started");
            assertFalse(received(harness.context()).stream().anyMatch(value -> value.startsWith("first")));
            ReloadTck.assertFollowsReload(harness, JMSReloadTest::listener);
            pool = null;
            factory = null;

            // neither the listeners of the first generation, their sessions, the retained pool nor the
            // development-only reloader keep it reachable
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    @Test
    void aChangeUnderTheJmsPrefixReleasesThePool() throws Exception {
        Map<String, String> properties = Artemis.properties();
        try (Connection connection = connectionFactory(properties).createConnection();
             Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
             MessageProducer producer = session.createProducer(session.createQueue(QUEUE));
             ReloadHarness harness = ReloadHarness.inDirectory(project)) {
            properties.forEach(harness::property);
            harness.source("example.Listener", LISTENER.formatted(QUEUE, "first"));
            harness.start();
            JMSConnectionPool first = harness.context().getBean(JMSConnectionPool.class);

            // the application properties change under micronaut.jms, together with a class, so the application restarts
            StringBuilder changed = new StringBuilder();
            properties.forEach((key, value) -> changed.append(key).append('=').append(value).append('\n'));
            changed.append("micronaut.jms.max-pool-size=5\n");
            harness.resource("application.properties", changed.toString());
            harness.source("example.Listener", LISTENER.formatted(QUEUE, "second"));
            harness.reload();
            assertEquals(2, harness.generation());

            JMSConnectionPool second = harness.context().getBean(JMSConnectionPool.class);
            assertNotSame(first, second, "a change under micronaut.jms releases the pool");
            assertTrue(second.toString().contains("maxSize=5"), second.toString());
            send(session, producer, "two");
            awaitTrue("the second generation receives", () -> received(harness.context()).contains("second two"));
            first = null;
            second = null;
            ReloadTck.assertRetiredGenerationsCollected(harness);
        }
    }

    private static ConnectionFactory connectionFactory(Map<String, String> properties) {
        return new ActiveMQJMSConnectionFactory(properties.get("micronaut.jms.activemq.artemis.connection-string"),
            properties.get("micronaut.jms.activemq.artemis.username"), properties.get("micronaut.jms.activemq.artemis.password"));
    }

    private static void send(Session session, MessageProducer producer, String value) throws Exception {
        producer.send(session.createTextMessage(value));
    }

    private static long millisSince(long start) {
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    /**
     * Tells the running generation that a class was redefined in place, as the development runtime does after it
     * redefined the class. The context is not kept: a reference to it would keep the generation reachable.
     */
    private static void changedInPlace(ReloadHarness harness, String className) {
        ApplicationContext context = harness.context();
        context.publishEvent(new ClassChangeEvent(JMSReloadTest.class, harness.generation(), Set.of(), context.getClassLoader(),
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

    private static void assertReloaderPresent(ApplicationContext context) {
        // the bean that follows changes in place exists in development mode only
        assertTrue(context.containsBean(type(context, "io.micronaut.jms.configuration.DevelopmentJMSReloader")));
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
