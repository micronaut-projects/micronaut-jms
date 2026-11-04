package io.micronaut.jms.observation;

import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.jms.model.JMSDestinationType;
import jakarta.jms.Message;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.micronaut.jms.observation.ObservationStubs.message;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JMSObservationsTest {

    @Test
    void observationsStartInOrderAndCompleteInReverseOrder() {
        List<String> events = new CopyOnWriteArrayList<>();
        ObservationStubs.RecordingObserver first = new ObservationStubs.RecordingObserver("first", 1);
        ObservationStubs.RecordingObserver second = new ObservationStubs.RecordingObserver("second", 2);
        JMSObservations observations = new JMSObservations();
        assertTrue(observations.isEmpty());
        assertEquals(List.of(), observations.process(JMSDestinationType.QUEUE, "orders", message("a", null, null)));

        observations.add(List.of(second, first));
        assertFalse(observations.isEmpty());
        List<JMSObservation> started = observations.send(JMSDestinationType.TOPIC, "prices");
        started.add(0, error -> events.add("first completed"));
        JMSObservations.beforeSend(started, message("a", null, null));
        JMSObservations.complete(started, new IllegalStateException());

        assertEquals(List.of(
            "first send TOPIC prices",
            "first send TOPIC prices before send",
            "first send TOPIC prices completed with IllegalStateException"), first.events);
        assertEquals(List.of(
            "second send TOPIC prices",
            "second send TOPIC prices before send",
            "second send TOPIC prices completed with IllegalStateException"), second.events);
        assertEquals(List.of("first completed"), events);
    }

    @Test
    void failingObserversAndObserversWithoutObservationAreSkipped() {
        ObservationStubs.RecordingObserver recording = new ObservationStubs.RecordingObserver("recording", 0);
        JMSMessageObserver failing = new JMSMessageObserver() {
            @Override
            public JMSObservation process(JMSDestinationType destinationType, String destination, Message message) {
                throw new IllegalStateException("observer failed");
            }

            @Override
            public JMSObservation send(JMSDestinationType destinationType, String destination) {
                return new JMSObservation() {
                    @Override
                    public void beforeSend(Message message) {
                        throw new IllegalStateException("before send failed");
                    }

                    @Override
                    public void complete(@Nullable Throwable error) {
                        throw new IllegalStateException("completion failed");
                    }
                };
            }
        };
        JMSMessageObserver silent = new JMSMessageObserver() { };
        assertNull(silent.process(JMSDestinationType.QUEUE, "orders", message("a", null, null)));
        assertNull(silent.send(JMSDestinationType.QUEUE, "orders"));
        JMSObservation completionOnly = error -> { };
        completionOnly.beforeSend(message("a", null, null));

        JMSObservations observations = new JMSObservations();
        observations.add(List.of(failing, silent, recording));
        List<JMSObservation> processing = observations.process(JMSDestinationType.QUEUE, "orders", message("a", null, null));
        JMSObservations.complete(processing, null);
        List<JMSObservation> sending = observations.send(JMSDestinationType.QUEUE, "orders");
        JMSObservations.beforeSend(sending, message("a", null, null));
        JMSObservations.complete(sending, null);

        assertEquals(1, processing.size());
        assertEquals(2, sending.size());
        assertEquals(List.of(
            "recording process QUEUE orders",
            "recording process QUEUE orders completed",
            "recording send QUEUE orders",
            "recording send QUEUE orders before send",
            "recording send QUEUE orders completed"), recording.events);
    }

    @Test
    void observationsGiveThePropagatedContextInTheOrderOfTheirStart() {
        record Element(String name) implements PropagatedContextElement { }
        JMSObservation first = new JMSObservation() {
            @Override
            public PropagatedContext propagatedContext(PropagatedContext context) {
                return context.plus(new Element("first"));
            }

            @Override
            public void complete(@Nullable Throwable error) {
                // nothing to complete
            }
        };
        JMSObservation failing = new JMSObservation() {
            @Override
            public PropagatedContext propagatedContext(PropagatedContext context) {
                throw new IllegalStateException("propagation failed");
            }

            @Override
            public void complete(@Nullable Throwable error) {
                // nothing to complete
            }
        };
        JMSObservation unchanged = error -> { };
        PropagatedContext empty = PropagatedContext.empty();

        assertSame(empty, JMSObservations.propagatedContext(List.of(unchanged), empty));
        PropagatedContext context = JMSObservations.propagatedContext(List.of(first, failing, unchanged), empty);

        assertEquals(List.of(new Element("first")), context.getAllElements());
    }

    @Test
    void anObserverThatFailsWithAnErrorCompletesTheStartedObservationsWithIt() {
        ObservationStubs.RecordingObserver recording = new ObservationStubs.RecordingObserver("recording", 0);
        ObservationStubs.RecordingObserver notStarted = new ObservationStubs.RecordingObserver("not started", 2);
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
        JMSObservations observations = new JMSObservations();
        observations.add(List.of(notStarted, failing, recording));

        assertSame(failure, assertThrows(AssertionError.class,
            () -> observations.process(JMSDestinationType.QUEUE, "orders", message("a", null, null))));

        assertEquals(List.of(
            "recording process QUEUE orders",
            "recording process QUEUE orders completed with AssertionError"), recording.events);
        assertEquals(List.of(), notStarted.events);
    }

    @Test
    void anObservationThatFailsWithAnErrorStillLetsTheOthersComplete() {
        List<String> events = new CopyOnWriteArrayList<>();
        AssertionError failure = new AssertionError("completion failed");
        List<JMSObservation> started = List.of(
            error -> events.add("first completed"),
            error -> {
                throw failure;
            },
            error -> events.add("third completed"));

        assertSame(failure, assertThrows(AssertionError.class, () -> JMSObservations.complete(started, null)));
        assertEquals(List.of("third completed", "first completed"), events);

        JMSObservation failingBeforeSend = new JMSObservation() {
            @Override
            public void beforeSend(Message message) {
                throw failure;
            }

            @Override
            public void complete(@Nullable Throwable error) {
            }
        };
        assertSame(failure, assertThrows(AssertionError.class,
            () -> JMSObservations.beforeSend(List.of(failingBeforeSend), message("a", null, null))));
    }
}
