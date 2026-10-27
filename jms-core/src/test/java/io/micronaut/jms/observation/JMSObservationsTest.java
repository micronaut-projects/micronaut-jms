package io.micronaut.jms.observation;

import io.micronaut.jms.model.JMSDestinationType;
import jakarta.jms.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.micronaut.jms.observation.ObservationStubs.message;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        List<JMSObservation> started = observations.send(JMSDestinationType.TOPIC, "prices", message("a", null, null));
        started.add(0, error -> events.add("first completed"));
        JMSObservations.complete(started, new IllegalStateException());

        assertEquals(List.of(
            "first send TOPIC prices",
            "first send TOPIC prices completed with IllegalStateException"), first.events);
        assertEquals(List.of(
            "second send TOPIC prices",
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
            public JMSObservation send(JMSDestinationType destinationType, String destination, Message message) {
                return error -> {
                    throw new IllegalStateException("completion failed");
                };
            }
        };
        JMSMessageObserver silent = new JMSMessageObserver() { };
        assertNull(silent.process(JMSDestinationType.QUEUE, "orders", message("a", null, null)));
        assertNull(silent.send(JMSDestinationType.QUEUE, "orders", message("a", null, null)));

        JMSObservations observations = new JMSObservations();
        observations.add(List.of(failing, silent, recording));
        List<JMSObservation> processing = observations.process(JMSDestinationType.QUEUE, "orders", message("a", null, null));
        JMSObservations.complete(processing, null);
        List<JMSObservation> sending = observations.send(JMSDestinationType.QUEUE, "orders", message("a", null, null));
        JMSObservations.complete(sending, null);

        assertEquals(1, processing.size());
        assertEquals(2, sending.size());
        assertEquals(List.of(
            "recording process QUEUE orders",
            "recording process QUEUE orders completed",
            "recording send QUEUE orders",
            "recording send QUEUE orders completed"), recording.events);
    }
}
