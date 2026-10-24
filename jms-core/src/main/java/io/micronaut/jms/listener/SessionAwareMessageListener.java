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
import jakarta.jms.MessageListener;
import jakarta.jms.Session;

/**
 * A {@link MessageListener} that also receives the {@link Session} that consumed the message, so that it can reply,
 * commit or roll back on that session. {@link JMSListener} passes its session to a listener of this type.
 *
 * @since 5.2.0
 */
@FunctionalInterface
public interface SessionAwareMessageListener extends MessageListener {

    /**
     * Handles a message.
     *
     * @param message the message
     * @param session the session that consumed the message
     * @throws JMSException if the message cannot be handled
     */
    void onMessage(Message message, Session session) throws JMSException;

    /**
     * Not supported: this listener needs the session that consumed the message.
     *
     * @param message the message
     */
    @Override
    default void onMessage(Message message) {
        throw new UnsupportedOperationException("A SessionAwareMessageListener needs the session that consumed the message");
    }
}
