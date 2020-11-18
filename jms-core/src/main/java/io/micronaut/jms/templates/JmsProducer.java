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

import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.model.MessageHeader;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Optional;

public class JmsProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsProducer.class);

    @Nullable
    private JMSConnectionPool connectionPool;
    private Serializer<Object> serializer;
    private boolean sessionTransacted = false;
    private int sessionAcknowledged = Session.AUTO_ACKNOWLEDGE;
    private final JMSDestinationType type;
    private int defaultJMSPriority = 4;

    public JmsProducer(JMSDestinationType type) {
        this.type = type;
    }

    public JmsProducer(
            JMSDestinationType type,
            boolean sessionTransacted,
            int sessionAcknowledged) {
        this.type = type;
        this.sessionTransacted = sessionTransacted;
        this.sessionAcknowledged = sessionAcknowledged;
    }

    /***
     * @return the {@link JMSConnectionPool} configured for the producer.
     */
    public JMSConnectionPool getConnectionPool() {
        return Optional.ofNullable(connectionPool).orElseThrow(
                () -> new IllegalStateException("Connection Pool cannot be null"));
    }

    /***
     *
     * Sets the {@link JMSConnectionPool} to be used by the producer.
     *
     * @param connectionPool
     */
    public void setConnectionPool(@Nullable JMSConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    /***
     *
     * Sets the {@link Serializer} to be used by the producer to
     *      convert the {@link Object} into an {@link Message}.
     *
     * @see io.micronaut.jms.serdes.DefaultSerializerDeserializer
     *
     * @param serializer
     */
    public void setSerializer(Serializer<Object> serializer) {
        this.serializer = serializer;
    }

    /***
     * @return the {@link Serializer} to be used by the producer.
     */
    public Serializer<Object> getSerializer() {
        return Optional.ofNullable(serializer).orElseThrow(
                () -> new IllegalStateException("Serializer cannot be null"));
    }

    /***
     *
     * Sends the given {@param message} to the {@param destination}
     *      with the given {@param headers}.
     *
     * @param destination
     * @param message
     * @param headers
     */
    public void send(
            @NotEmpty String destination,
            @NotNull Object message,
            MessageHeader... headers) {
        try (Connection connection = getConnectionPool().createConnection();
             Session session = getOrCreateSession(connection)) {
            send(destination, getSerializer().serialize(session, message), headers);
        } catch (JMSException e) {
            e.printStackTrace();
        }

    }

    /***
     *
     * Sends the given {@param message} to the {@param destination}
     *      with the given {@param headers}.
     *
     * @param destination
     * @param message
     * @param headers
     */
    public void send(
            @NotEmpty String destination,
            @NotNull Message message,
            MessageHeader... headers) {
        send(lookupDestination(destination), message, headers);
    }

    private Destination lookupDestination(String destination) {
        try (Connection connection = getConnectionPool().createConnection();
             Session session = getOrCreateSession(connection)) {
            return type == JMSDestinationType.QUEUE ?
                    session.createQueue(destination) :
                    session.createTopic(destination);
        } catch (JMSException e) {
            e.printStackTrace();
        }
        return null;
    }

    /***
     *
     * Sends the given {@param message} to the {@param destination}
     *      with the given {@param headers}.
     *
     * @param destination
     * @param message
     * @param headers
     */
    public void send(
            @NotNull Destination destination,
            @NotNull Message message,
            MessageHeader... headers) {
        try (Connection connection = getConnectionPool().createConnection();
             Session session = getOrCreateSession(connection)) {
            send(session, destination, message, headers);
        } catch (JMSException e) {
            LOGGER.error("Exception occurred while sending message ", e);
        }
    }

    private Session getOrCreateSession(Connection connection) throws JMSException {
        return connection.createSession(sessionTransacted, sessionAcknowledged);
    }

    private void send(
            @NotNull Session session,
            @NotNull Destination destination,
            @NotNull Message message,
            MessageHeader... headers) throws JMSException {
        notNull(session, "Session cannot be null");
        notNull(destination, "Destination cannot be null");
        notNull(message, "Message cannot be null");

        try (MessageProducer producer = session.createProducer(destination)) {
            send(producer, message, headers);
            if (sessionTransacted) {
                session.commit();
            }
        } catch (JMSException e) {
            session.rollback();
            LOGGER.error("Error sending the message.", e);
        }

    }

    private void send(@NotNull MessageProducer producer, @NotNull Message message, MessageHeader... headers) throws JMSException {
        notNull(producer, "MessageProducer cannot be null");
        notNull(message, "Message cannot be null");
        setJMSHeaders(message, headers);
        producer.send(message, Message.DEFAULT_DELIVERY_MODE, message.getJMSPriority(), Message.DEFAULT_TIME_TO_LIVE);
    }

    private static void notNull(Object object, String failureMessage) {
        Optional.ofNullable(object).orElseThrow(() -> new IllegalStateException(failureMessage));
    }

    private static void setJMSHeaders(@NotNull Message message, MessageHeader... headers) {
        for (MessageHeader header : headers) {
            if (header.isJMSHeader()) {
                header.setJMSHeader(message);
            } else {
                header.setHeader(message);
            }
        }
    }
}