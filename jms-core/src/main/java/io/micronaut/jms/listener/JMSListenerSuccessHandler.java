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
package io.micronaut.jms.listener;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;

/**
 * Handles an action after a message has been received and processed by a {@link JMSListener}.
 *
 * @author Elliott Pope
 * @since 2.1.1
 *
 * @see TransactionalJMSListenerSuccessHandler
 */
@FunctionalInterface
public interface JMSListenerSuccessHandler extends Ordered {

    int DEFAULT_POSITION = 100;

    /**
     * Handle the successfully processed message.
     *
     * @param session - the {@link Session} the {@link JMSListener} is bound to.
     * @param message - the {@link Message} that was processed.
     * @throws JMSException if any exception occurs while handling the message.
     */
    void handle(@NonNull Session session, @NonNull Message message) throws JMSException;

    @Override
    default int getOrder() {
        return DEFAULT_POSITION;
    }
}
