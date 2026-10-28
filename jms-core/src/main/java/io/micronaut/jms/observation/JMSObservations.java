/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.jms.observation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.jms.model.JMSDestinationType;
import jakarta.jms.Message;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * The observers of the messages of a listener or a producer. An observer that fails with an exception is logged, so
 * that it does not stop a message from being processed or sent. One that fails with an {@link Error} completes the
 * observations already started with it and rethrows it.
 *
 * @since 5.2.0
 */
@Internal
public final class JMSObservations {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSObservations.class);

    private final List<JMSMessageObserver> observers = new ArrayList<>();

    /**
     * @param observers The observers to add
     */
    public void add(Collection<? extends JMSMessageObserver> observers) {
        this.observers.addAll(observers);
        this.observers.sort(OrderUtil.COMPARATOR);
    }

    /**
     * @return Whether there are no observers
     */
    public boolean isEmpty() {
        return observers.isEmpty();
    }

    /**
     * Starts the observations of a message that a listener processes.
     *
     * @param destinationType The type of the destination
     * @param destination     The name of the destination
     * @param message         The message
     * @return The started observations, to complete with {@link #complete(List, Throwable)}
     */
    public List<JMSObservation> process(JMSDestinationType destinationType, String destination, Message message) {
        return start(observer -> observer.process(destinationType, destination, message));
    }

    /**
     * Starts the observations of a message that a producer sends, before its connection is taken.
     *
     * @param destinationType The type of the destination
     * @param destination     The name of the destination
     * @return The started observations, to pass the message to with {@link #beforeSend(List, Message)} and to
     * complete with {@link #complete(List, Throwable)}
     */
    public List<JMSObservation> send(JMSDestinationType destinationType, String destination) {
        return start(observer -> observer.send(destinationType, destination));
    }

    /**
     * Passes the message that is about to be sent to observations, in the order of their start.
     *
     * @param observations The observations
     * @param message      The message
     */
    public static void beforeSend(List<JMSObservation> observations, Message message) {
        for (JMSObservation observation : observations) {
            try {
                observation.beforeSend(message);
            } catch (RuntimeException e) {
                LOGGER.warn("JMS message observation failed before the send: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Gives observations of a message that a listener processes, in the order of their start, the propagated context
     * to process the message in.
     *
     * @param observations The observations
     * @param context      The current propagated context
     * @return The propagated context to process the message in, the given one if no observation changes it
     */
    public static PropagatedContext propagatedContext(List<JMSObservation> observations, PropagatedContext context) {
        PropagatedContext result = context;
        for (JMSObservation observation : observations) {
            try {
                result = observation.propagatedContext(result);
            } catch (RuntimeException e) {
                LOGGER.warn("JMS message observation failed to propagate its context: {}", e.getMessage(), e);
            }
        }
        return result;
    }

    /**
     * Completes observations in the reverse order of their start. An {@link Error} of an observation is rethrown once
     * the others are completed.
     *
     * @param observations The observations
     * @param error        The error, or {@code null}
     */
    public static void complete(List<JMSObservation> observations, @Nullable Throwable error) {
        complete(observations, observations.size() - 1, error);
    }

    private static void complete(List<JMSObservation> observations, int index, @Nullable Throwable error) {
        if (index < 0) {
            return;
        }
        try {
            observations.get(index).complete(error);
        } catch (RuntimeException e) {
            LOGGER.warn("JMS message observation failed to complete: {}", e.getMessage(), e);
        } finally {
            // also when the observation failed with an error, which propagates once the others are completed
            complete(observations, index - 1, error);
        }
    }

    private List<JMSObservation> start(Function<JMSMessageObserver, @Nullable JMSObservation> start) {
        if (observers.isEmpty()) {
            return List.of();
        }
        List<JMSObservation> observations = new ArrayList<>(observers.size());
        for (JMSMessageObserver observer : observers) {
            try {
                JMSObservation observation = start.apply(observer);
                if (observation != null) {
                    observations.add(observation);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("JMS message observer {} failed: {}", observer, e.getMessage(), e);
            } catch (Error e) {
                // the message is not processed or sent, so the observations already started end with the error
                complete(observations, e);
                throw e;
            }
        }
        return observations;
    }
}
