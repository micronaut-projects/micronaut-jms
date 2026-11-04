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

import io.micronaut.core.order.Ordered;
import io.micronaut.jms.model.JMSDestinationType;
import jakarta.jms.Message;
import org.jspecify.annotations.Nullable;

/**
 * Observes the messages that JMS listeners process and that JMS producers send, for example to record metrics or
 * traces. Each observer bean is called before a message is processed or sent, and completes the observation that it
 * returns afterwards. Observers are called in {@link #getOrder() order} and their observations are completed in the
 * reverse order. An observer that throws an exception is logged and skipped. One that throws an {@link Error} stops
 * the processing or the send: the observations already started are completed with the error, which is rethrown.
 *
 * @since 5.2.0
 */
public interface JMSMessageObserver extends Ordered {

    /**
     * Called before a listener processes a message: before the listener method and the success and error handlers.
     *
     * @param destinationType The type of the destination
     * @param destination     The name of the destination the listener consumes from
     * @param message         The message
     * @return The observation to complete once the message is processed, or {@code null}
     */
    default @Nullable JMSObservation process(JMSDestinationType destinationType, String destination, Message message) {
        return null;
    }

    /**
     * Called before a producer sends a message, before the connection is taken from the pool and the message is
     * created, so a send that fails to connect is observed too. Once the message is created and its headers are set,
     * the observation's {@link JMSObservation#beforeSend(Message)} is called with it, so properties added to the
     * message are sent with it.
     *
     * @param destinationType The type of the destination
     * @param destination     The name of the destination the message is sent to
     * @return The observation to complete once the message is sent or the send failed, or {@code null}
     */
    default @Nullable JMSObservation send(JMSDestinationType destinationType, String destination) {
        return null;
    }
}
