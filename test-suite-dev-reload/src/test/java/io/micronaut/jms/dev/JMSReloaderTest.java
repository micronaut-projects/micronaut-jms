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
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.bind.JMSArgumentBinderRegistry;
import io.micronaut.jms.configuration.JMSQueueListenerMethodProcessor;
import io.micronaut.jms.listener.GlobalJMSListenerErrorHandler;
import io.micronaut.jms.listener.GlobalJMSListenerSuccessHandler;
import io.micronaut.jms.listener.JMSListenerRegistry;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.serdes.Serializer;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.Message;
import jakarta.jms.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The development reloader against a broker: what each change recreates, and that the listeners consume again,
 * exactly once, on top of the new beans. Outside development mode the same beans and the same events change nothing.
 */
class JMSReloaderTest {

    static final String QUEUE = "dev-reload";
    private static final String SPEC = "JMSReloaderTest";
    private static final String RELOADER = "io.micronaut.jms.configuration.DevelopmentJMSReloader";

    private ApplicationContext context;

    @AfterEach
    void stop() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void anInPlaceChangeOfAListenerClassRestartsTheListenersOnANewListenerAndARestartOrAnUnrelatedChangeDoesNot() throws Exception {
        devContext(true);
        JMSListenerRegistry registry = context.getBean(JMSListenerRegistry.class);
        ReloadListener listener = context.getBean(ReloadListener.class);
        assertTrue(context.containsBean(reloader()), "the reloader exists in development mode");
        sendAndAwait(listener, "one");

        // the application restarts: the new context starts its own listeners
        context.publishEvent(classChange(Set.of(ReloadListener.class.getClassLoader()), List.of(), ReloadStrategy.RESTART));
        assertSame(registry, context.getBean(JMSListenerRegistry.class));

        // a class that is not a listener is redefined in place
        context.publishEvent(classChange(Set.of(), List.of(new ClassChange(JMSReloaderTest.class.getName(), ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));
        assertSame(registry, context.getBean(JMSListenerRegistry.class));
        assertSame(listener, context.getBean(ReloadListener.class));

        // the listener class is redefined in place
        context.publishEvent(classChange(Set.of(), List.of(new ClassChange(ReloadListener.class.getName(), ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));
        ReloadListener recreated = context.getBean(ReloadListener.class);
        assertNotSame(registry, context.getBean(JMSListenerRegistry.class));
        assertNotSame(listener, recreated);

        // every message reaches the new listener: no consumer of the previous one is left on the queue
        for (int i = 0; i < 4; i++) {
            sendAndAwait(recreated, "two-" + i);
        }
        assertEquals(List.of("one"), listener.received);
    }

    @Test
    void repeatedInPlaceRestartsReturnTheListenerConnectionsToThePool() throws Exception {
        // a pool of two connections: one for the listener, one for the producer. A restart that kept the connection
        // of the stopped listener would exhaust it on the second restart
        devContext(true, Map.of("micronaut.jms.max-pool-size", "2"));
        JMSConnectionPool pool = context.getBean(JMSConnectionPool.class);
        sendAndAwait(context.getBean(ReloadListener.class), "zero");
        for (int i = 0; i < 4; i++) {
            context.publishEvent(classChange(Set.of(), List.of(new ClassChange(ReloadListener.class.getName(), ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));
            sendAndAwait(context.getBean(ReloadListener.class), "restart-" + i);
        }
        assertSame(pool, context.getBean(JMSConnectionPool.class), "the pool is kept: only the listeners restart");
    }

    @Test
    void aSerializerChangeOrARetiredClassloaderRecreatesTheSerializerAndBinderRegistryAndRestartsTheListeners() throws Exception {
        devContext(true);
        DefaultSerializerDeserializer serDes = context.getBean(DefaultSerializerDeserializer.class);
        JMSArgumentBinderRegistry binders = context.getBean(JMSArgumentBinderRegistry.class);
        JMSListenerRegistry registry = context.getBean(JMSListenerRegistry.class);
        ReloadListener listener = context.getBean(ReloadListener.class);

        context.publishEvent(classChange(Set.of(), List.of(new ClassChange(UpperCaseSerializer.class.getName(), ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));
        assertNotSame(serDes, context.getBean(DefaultSerializerDeserializer.class));
        assertNotSame(binders, context.getBean(JMSArgumentBinderRegistry.class));
        assertNotSame(registry, context.getBean(JMSListenerRegistry.class));
        assertSame(listener, context.getBean(ReloadListener.class), "the listener bean is unchanged");
        sendAndAwait(listener, "one");

        binders = context.getBean(JMSArgumentBinderRegistry.class);
        context.publishEvent(classChange(Set.of(ReloadListener.class.getClassLoader()), List.of(), ReloadStrategy.RELOAD));
        assertNotSame(binders, context.getBean(JMSArgumentBinderRegistry.class));
        sendAndAwait(listener, "two");
        assertEquals(List.of("one", "two"), listener.received);
    }

    @Test
    void anInPlaceChangeOfAFactoryThatProducesASerializerRecreatesTheRegistries() throws Exception {
        devContext(true);
        JMSArgumentBinderRegistry binders = context.getBean(JMSArgumentBinderRegistry.class);
        context.publishEvent(classChange(Set.of(), List.of(new ClassChange(SerializerFactory.class.getName(), ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));
        assertNotSame(binders, context.getBean(JMSArgumentBinderRegistry.class));
        sendAndAwait(context.getBean(ReloadListener.class), "one");
    }

    @Test
    void aListenerDefinitionRegisteredWhileRunningRestartsTheListeners() throws Exception {
        devContext(true);
        JMSListenerRegistry registry = context.getBean(JMSListenerRegistry.class);
        BeanDefinition<ReloadListener> definition = context.getBeanDefinition(ReloadListener.class);

        // the launcher swaps the definition of the listener for another of the same class
        ((DefaultBeanContext) context).notifyDefinitionChange(List.of(definition), List.of(definition));

        assertNotSame(registry, context.getBean(JMSListenerRegistry.class));
        ReloadListener listener = context.getBean(ReloadListener.class);
        for (int i = 0; i < 4; i++) {
            sendAndAwait(listener, "one-" + i);
        }
    }

    @Test
    void aDefinitionOfABeanThatIsBothAnErrorAndASuccessHandlerRestartsTheListenersOnce() throws Exception {
        devContext(true);
        BeanDefinition<BothHandlers> definition = context.getBeanDefinition(BothHandlers.class);
        int created = RegistryCreations.COUNT.get();
        Object processor = context.getBean(JMSQueueListenerMethodProcessor.class);

        ((DefaultBeanContext) context).notifyDefinitionChange(List.of(definition), List.of(definition));

        assertEquals(created + 1, RegistryCreations.COUNT.get(), "the registry of listeners was created again exactly once");
        assertNotSame(processor, context.getBean(JMSQueueListenerMethodProcessor.class));
        sendAndAwait(context.getBean(ReloadListener.class), "one");
    }

    @Test
    void aContextThatDoesNotTrackBeanDependenciesKeepsTheListenersRunning() throws Exception {
        devContext(false);
        JMSListenerRegistry registry = context.getBean(JMSListenerRegistry.class);
        ReloadListener listener = context.getBean(ReloadListener.class);
        assertTrue(context.containsBean(reloader()));

        context.publishEvent(classChange(Set.of(), List.of(new ClassChange(ReloadListener.class.getName(), ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));

        assertSame(registry, context.getBean(JMSListenerRegistry.class));
        assertSame(listener, context.getBean(ReloadListener.class));
        sendAndAwait(listener, "one");
    }

    @Test
    void outsideDevelopmentModeThereIsNoReloaderAndTheSameBeansKeepTheirListenersThroughClassChanges() throws Exception {
        context = ApplicationContext.builder()
            .properties(properties(Map.of()))
            .start();
        assertFalse(context.containsBean(reloader()));
        JMSListenerRegistry registry = context.getBean(JMSListenerRegistry.class);
        DefaultSerializerDeserializer serDes = context.getBean(DefaultSerializerDeserializer.class);
        ReloadListener listener = context.getBean(ReloadListener.class);

        context.publishEvent(classChange(Set.of(ReloadListener.class.getClassLoader()),
            List.of(new ClassChange(ReloadListener.class.getName(), ClassChange.Kind.MODIFIED)), ReloadStrategy.RELOAD));

        assertSame(registry, context.getBean(JMSListenerRegistry.class));
        assertSame(serDes, context.getBean(DefaultSerializerDeserializer.class));
        assertSame(listener, context.getBean(ReloadListener.class));
        sendAndAwait(listener, "one");
        assertEquals(List.of("one"), listener.received);
    }

    @Test
    void aPoolSizeBelowOneIsRejectedByTheConfigurationProperties() {
        Map<String, Object> properties = properties(Map.of("micronaut.jms.max-pool-size", "0"));
        Throwable failure = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> {
            try (ApplicationContext started = ApplicationContext.builder().properties(properties).start()) {
                started.getBean(JMSConnectionPool.class);
            }
        });
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        assertTrue(messages.toString().contains("JMSConfigurationProperties.getMaxPoolSize - must be greater than or equal to 1"), messages.toString());
    }

    private void devContext(boolean track) {
        devContext(track, Map.of());
    }

    private void devContext(boolean track, Map<String, String> extra) {
        Map<String, Object> properties = properties(extra);
        properties.put("micronaut.dev.enabled", "true");
        context = ApplicationContext.builder()
            .properties(properties)
            .trackBeanDependencies(track)
            .start();
    }

    private static Map<String, Object> properties(Map<String, String> extra) {
        Map<String, Object> properties = new HashMap<>(Artemis.properties());
        properties.put("spec.name", SPEC);
        properties.putAll(extra);
        return properties;
    }

    private void sendAndAwait(ReloadListener listener, String value) throws InterruptedException {
        context.getBean(ReloadProducer.class).send(value);
        awaitTrue(listener + " receives " + value, () -> listener.received.contains(value));
    }

    private ClassChangeEvent classChange(Set<ClassLoader> retired, List<ClassChange> changes, ReloadStrategy strategy) {
        return new ClassChangeEvent(this, 1, retired, JMSReloaderTest.class.getClassLoader(), changes, strategy);
    }

    private static Class<?> reloader() throws ClassNotFoundException {
        return Class.forName(RELOADER);
    }

    static void awaitTrue(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting until " + what);
            }
            Thread.sleep(50);
        }
    }

    @JMSListener(CONNECTION_FACTORY_BEAN_NAME)
    @Requires(property = "spec.name", value = SPEC)
    static class ReloadListener {
        final List<String> received = new CopyOnWriteArrayList<>();

        @Queue(QUEUE)
        void receive(@MessageBody String value) {
            received.add(value);
        }
    }

    @JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
    @Requires(property = "spec.name", value = SPEC)
    interface ReloadProducer {
        @Queue(QUEUE)
        void send(@MessageBody String value);
    }

    @Singleton
    @Named("upper")
    @Requires(property = "spec.name", value = SPEC)
    static class UpperCaseSerializer implements Serializer {
        @Override
        public Message serialize(Session session, Object body) {
            try {
                return session.createTextMessage(String.valueOf(body).toUpperCase());
            } catch (jakarta.jms.JMSException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC)
    static class SerializerFactory {
        @Singleton
        @Named("factory")
        Serializer factorySerializer() {
            return new UpperCaseSerializer();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class BothHandlers implements GlobalJMSListenerErrorHandler, GlobalJMSListenerSuccessHandler {
        @Override
        public void handle(Session session, Message message, Throwable ex) {
        }

        @Override
        public void handle(Session session, Message message) {
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class RegistryCreations implements BeanCreatedEventListener<JMSListenerRegistry> {
        static final AtomicInteger COUNT = new AtomicInteger();

        @Override
        public JMSListenerRegistry onCreated(BeanCreatedEvent<JMSListenerRegistry> event) {
            COUNT.incrementAndGet();
            return event.getBean();
        }
    }
}
