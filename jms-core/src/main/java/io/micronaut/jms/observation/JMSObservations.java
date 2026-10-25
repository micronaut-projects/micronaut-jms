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
 * The observers of the messages of a listener or a producer. A failing observer is logged, so that it does not stop
 * a message from being processed or sent.
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
     * Starts the observations of a message that a producer sends.
     *
     * @param destinationType The type of the destination
     * @param destination     The name of the destination
     * @param message         The message
     * @return The started observations, to complete with {@link #complete(List, Throwable)}
     */
    public List<JMSObservation> send(JMSDestinationType destinationType, String destination, Message message) {
        return start(observer -> observer.send(destinationType, destination, message));
    }

    /**
     * Completes observations in the reverse order of their start.
     *
     * @param observations The observations
     * @param error        The error, or {@code null}
     */
    public static void complete(List<JMSObservation> observations, @Nullable Throwable error) {
        for (int i = observations.size() - 1; i >= 0; i--) {
            try {
                observations.get(i).complete(error);
            } catch (RuntimeException e) {
                LOGGER.warn("JMS message observation failed to complete: {}", e.getMessage(), e);
            }
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
            }
        }
        return observations;
    }
}
