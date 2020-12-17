/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.jms.templates;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.model.MessageHeader;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.serdes.Serializer;
import io.micronaut.messaging.exceptions.MessageListenerException;
import io.micronaut.messaging.exceptions.MessagingClientException;
import io.micronaut.messaging.exceptions.MessagingSystemException;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;
import static javax.jms.Message.DEFAULT_DELIVERY_MODE;
import static javax.jms.Message.DEFAULT_TIME_TO_LIVE;
import static javax.jms.Session.AUTO_ACKNOWLEDGE;

/**
 * Helper class that sends messages, configuring JMS connections, sessions,
 * etc. for you.
 * <p>
 * TODO rename, too similar to JMSProducer
 *
 * @param <T> the message type
 * @author Elliott Pope
 * @since 1.0.0
 */
public class JmsProducer<T> {

    private final JMSDestinationType type;
    private final JMSConnectionPool connectionPool;
    private final Serializer<T> serializer;
    private final boolean sessionTransacted;
    private final int sessionAcknowledgeMode;

    @SuppressWarnings("unchecked")
    public JmsProducer(JMSDestinationType type,
                       JMSConnectionPool connectionPool) {
        this(type, connectionPool, (Serializer<T>) DefaultSerializerDeserializer.getInstance());
    }

    public JmsProducer(JMSDestinationType type,
                       JMSConnectionPool connectionPool,
                       Serializer<T> serializer) {
        this(type, connectionPool, serializer, false, AUTO_ACKNOWLEDGE);
    }

    public JmsProducer(JMSDestinationType type,
                       JMSConnectionPool connectionPool,
                       Serializer<T> serializer,
                       boolean sessionTransacted,
                       int sessionAcknowledgeMode) {
        this.type = type;
        this.connectionPool = connectionPool;
        this.serializer = serializer;
        this.sessionTransacted = sessionTransacted;
        this.sessionAcknowledgeMode = sessionAcknowledgeMode;
    }

    /**
     * Creates a {@link Message} from the {@code body} and sends it to the
     * {@code destination} with the {@code headers}.
     *
     * @param destination the queue or topic name
     * @param body        the body
     * @param headers     optional headers
     */
    public void send(@NonNull String destination,
                     @NonNull T body,
                     MessageHeader... headers) {
        try (Connection connection = connectionPool.createConnection();
             Session session = createSession(connection)) {
            send(destination, serializer.serialize(session, body), headers);
        } catch (JMSException | RuntimeException e) {
            throw new MessagingClientException("Problem sending message to " + destination, e);
        }
    }

    /**
     * Sends the given {@code message} to the {@code destination} with the
     * given {@code headers}.
     *
     * @param destination the queue or topic name
     * @param message     the message
     * @param headers     optional headers
     */
    public void send(@NonNull String destination,
                     @NonNull Message message,
                     MessageHeader... headers) {
        send(lookupDestination(destination), message, headers);
    }

    /**
     * Sends the given {@code message} to the {@code destination}
     * with the given {@code headers}.
     *
     * @param destination the queue or topic name
     * @param message     the message
     * @param headers     optional headers
     */
    public void send(@NonNull Destination destination,
                     @NonNull Message message,
                     MessageHeader... headers) {
        ArgumentUtils.requireNonNull("destination", destination);
        ArgumentUtils.requireNonNull("message", message);

        try (Connection connection = connectionPool.createConnection();
             Session session = createSession(connection)) {
            send(session, destination, message, headers);
        } catch (JMSException | RuntimeException e) {
            throw new MessagingClientException("Problem sending message ", e);
        }
    }

    private void send(@NonNull Session session,
                      @NonNull Destination destination,
                      @NonNull Message message,
                      MessageHeader... headers) throws JMSException {
        ArgumentUtils.requireNonNull("session", session);

        try (MessageProducer producer = session.createProducer(destination)) {

            for (MessageHeader header : headers) {
                header.apply(message);
            }

            // TODO support specifying delivery mode, TTL, priority
            producer.send(message, DEFAULT_DELIVERY_MODE, message.getJMSPriority(), DEFAULT_TIME_TO_LIVE);

            if (sessionTransacted) {
                session.commit();
            }
        } catch (JMSException | RuntimeException e) {
            if (sessionTransacted) {
                try {
                    session.rollback();
                } catch (JMSException | RuntimeException e2) {
                    throw new MessageListenerException(
                        "Problem rolling back transaction", e2);
                }
            }
            throw new MessagingClientException("Problem sending the message", e);
        }
    }

    private Destination lookupDestination(String destination) {
        try (Connection connection = connectionPool.createConnection();
             Session session = createSession(connection)) {
            return type == QUEUE ?
                session.createQueue(destination) :
                session.createTopic(destination);
        } catch (JMSException | RuntimeException e) {
            throw new MessagingSystemException("Problem creating " +
                type.name().toLowerCase() + " '" + destination + "'", e);
        }
    }

    private Session createSession(Connection connection) throws JMSException {
        return connection.createSession(sessionTransacted, sessionAcknowledgeMode);
    }
}
