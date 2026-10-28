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

import io.micronaut.core.propagation.PropagatedContext;
import jakarta.jms.Message;
import org.jspecify.annotations.Nullable;

/**
 * The observation of a message that a {@link JMSMessageObserver} started.
 *
 * @since 5.2.0
 */
@FunctionalInterface
public interface JMSObservation {

    /**
     * Called on the observation of a send once the message is created and its headers are set, just before it is
     * sent, so properties added to the message, such as a trace context, are sent with it. Not called when the send
     * fails before the message exists, nor on the observation of a message that a listener processes, whose message
     * is given when the observation starts.
     *
     * @param message The message to send
     */
    default void beforeSend(Message message) {
    }

    /**
     * Called on the observation of a message that a listener processes, before the listener method runs, to give
     * the propagated context that the listener method and the success and error handlers run in, for example with
     * the span of a trace added, so that it is current while they run and follows them to the threads that the
     * context propagates to. Not called on the observation of a send.
     *
     * @param context The propagated context, with the elements of the observations started before this one
     * @return The propagated context to process the message in, by default the given one
     */
    default PropagatedContext propagatedContext(PropagatedContext context) {
        return context;
    }

    /**
     * Completes the observation, on the thread that started it.
     *
     * @param error The error that processing or sending the message failed with, or {@code null}
     */
    void complete(@Nullable Throwable error);
}
