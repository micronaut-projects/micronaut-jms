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
 * commit or roll back on that session.
 *
 * <p>Only {@link JMSListener} calls {@link #onMessage(Message, Session)}, and it checks the session with
 * {@link #checkSession(Session)} before it consumes any message. This interface extends {@link MessageListener} so that
 * it can be passed to the {@link JMSListenerRegistry} {@code register} methods, but it cannot be used as a plain
 * {@code MessageListener}: {@link #onMessage(Message)} throws, so a decorator that delegates to it or
 * {@code MessageConsumer.setMessageListener} would fail on every message.</p>
 *
 * <p>A JMS session is single threaded: use the session only inside {@code onMessage}, on the calling thread. For that
 * reason, {@code JMSListener} rejects this listener when it is registered with an executor.</p>
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
     * Checks that this listener can handle the messages of a session, before {@link JMSListener} consumes any of them.
     * By default, any session is accepted.
     *
     * @param session the session of the listener
     * @throws JMSException if the session cannot be checked
     * @throws IllegalStateException if this listener cannot use the session
     */
    default void checkSession(Session session) throws JMSException {
        // any session
    }

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
