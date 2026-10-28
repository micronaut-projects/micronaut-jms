package io.micronaut.jms.listener;

import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.observation.JMSMessageObserver;
import io.micronaut.jms.observation.JMSObservation;
import io.micronaut.jms.observation.ObservationStubs.RecordingObserver;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.jms.observation.ObservationStubs.message;
import static io.micronaut.jms.observation.ObservationStubs.stub;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JMSListenerObservationTest {

    private final AtomicReference<MessageListener> consumerListener = new AtomicReference<>();

    @Test
    void observersOfTheRegistryObserveEveryMessageThatAListenerProcesses() throws JMSException {
        RecordingObserver observer = new RecordingObserver("recording", 0);
        JMSListenerSuccessHandler failingSuccessHandler = (session, message) -> {
            if ("unacknowledged".equals(text(message))) {
                throw new JMSException("acknowledge failed");
            }
        };
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of(), List.of(observer));
        registry.register(connection(), JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE, message -> {
            if ("failing".equals(text(message))) {
                throw new IllegalStateException("listener failed");
            }
        }, null, true, Optional.empty()).addSuccessHandlers(failingSuccessHandler);

        consumerListener.get().onMessage(message("ok", null, null));
        consumerListener.get().onMessage(message("failing", null, null));
        consumerListener.get().onMessage(message("unacknowledged", null, null));

        assertEquals(List.of(
            "recording process QUEUE orders",
            "recording process QUEUE orders completed",
            "recording process QUEUE orders",
            "recording process QUEUE orders completed with IllegalStateException",
            "recording process QUEUE orders",
            "recording process QUEUE orders completed with JMSException"), observer.events);
    }

    @Test
    void theListenerRunsInThePropagatedContextOfTheObservations() throws JMSException {
        record Element(String name) implements PropagatedContextElement { }
        JMSMessageObserver propagating = new JMSMessageObserver() {
            @Override
            public JMSObservation process(JMSDestinationType destinationType, String destination, Message message) {
                return new JMSObservation() {
                    @Override
                    public PropagatedContext propagatedContext(PropagatedContext context) {
                        return context.plus(new Element(destination));
                    }

                    @Override
                    public void complete(Throwable error) {
                        // nothing to complete
                    }
                };
            }
        };
        List<Object> elements = new CopyOnWriteArrayList<>();
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of(), List.of(propagating));
        registry.register(connection(), JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE,
            message -> elements.addAll(PropagatedContext.get().getAllElements()), null, true, Optional.empty())
            .addSuccessHandlers((session, message) -> elements.add(PropagatedContext.get().get(Element.class).name()));

        consumerListener.get().onMessage(message("ok", null, null));

        assertEquals(List.of(new Element("orders"), "orders"), elements);
        assertFalse(PropagatedContext.find().isPresent());
    }

    @Test
    void anErrorOfTheListenerCompletesTheObservationsAndIsRethrown() throws JMSException {
        RecordingObserver observer = new RecordingObserver("recording", 0);
        List<Throwable> handled = new CopyOnWriteArrayList<>();
        AssertionError failure = new AssertionError("listener failed");
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of(), List.of(observer));
        registry.register(connection(), JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE, message -> {
            throw failure;
        }, null, true, Optional.empty()).addErrorHandlers((session, message, error) -> handled.add(error));

        assertSame(failure, assertThrows(AssertionError.class,
            () -> consumerListener.get().onMessage(message("failing", null, null))));

        assertEquals(List.of(
            "recording process QUEUE orders",
            "recording process QUEUE orders completed with AssertionError"), observer.events);
        assertEquals(List.of(), handled, "errors are not given to the error handlers");
    }

    @Test
    void anErrorOfAnObserverCompletesTheObservationsStartedBeforeIt() throws JMSException {
        RecordingObserver observer = new RecordingObserver("recording", 0);
        AssertionError failure = new AssertionError("observer failed");
        JMSMessageObserver failing = new JMSMessageObserver() {
            @Override
            public JMSObservation process(JMSDestinationType destinationType, String destination, Message message) {
                throw failure;
            }

            @Override
            public int getOrder() {
                return 1;
            }
        };
        List<String> received = new CopyOnWriteArrayList<>();
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of(), List.of(failing, observer));
        registry.register(connection(), JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE,
            message -> received.add(text(message)), null, true, Optional.empty());

        assertSame(failure, assertThrows(AssertionError.class,
            () -> consumerListener.get().onMessage(message("ok", null, null))));

        assertEquals(List.of(), received);
        assertEquals(List.of(
            "recording process QUEUE orders",
            "recording process QUEUE orders completed with AssertionError"), observer.events);
    }

    @Test
    @SuppressWarnings("removal")
    void theRegistryWithoutObserversStillRegistersListeners() throws JMSException {
        List<String> received = new CopyOnWriteArrayList<>();
        JMSListenerRegistry registry = new JMSListenerRegistry(List.of(), List.of());
        registry.register(connection(), JMSDestinationType.QUEUE, "orders", false, Session.AUTO_ACKNOWLEDGE,
            message -> received.add(text(message)), null, true, Optional.empty());

        consumerListener.get().onMessage(message("ok", null, null));

        assertEquals(List.of("ok"), received);
    }

    private static String text(Message message) {
        try {
            return ((TextMessage) message).getText();
        } catch (JMSException e) {
            throw new IllegalStateException(e);
        }
    }

    private Connection connection() {
        MessageConsumer consumer = stub(MessageConsumer.class, (name, args) -> {
            if ("setMessageListener".equals(name)) {
                consumerListener.set((MessageListener) args[0]);
            }
            return null;
        });
        jakarta.jms.Queue queue = stub(jakarta.jms.Queue.class, (name, args) -> "orders");
        Session session = stub(Session.class, (name, args) -> switch (name) {
            case "createQueue" -> queue;
            case "createConsumer" -> consumer;
            default -> null;
        });
        return stub(Connection.class, (name, args) -> "createSession".equals(name) ? session : null);
    }
}
