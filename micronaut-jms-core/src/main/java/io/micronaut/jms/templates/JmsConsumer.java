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
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.Deserializer;

import javax.annotation.Nullable;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.validation.constraints.NotNull;
import java.util.Optional;

public class JmsConsumer {
    @Nullable
    private JMSConnectionPool connectionPool;
    @Nullable
    private Deserializer deserializer;
    private boolean sessionTransacted = false;
    private int sessionAcknowledged = Session.AUTO_ACKNOWLEDGE;
    private final JMSDestinationType type;

    public JmsConsumer(JMSDestinationType type) {
        this.type = type;
    }

    /***
     * @return the {@link JMSConnectionPool} configured for the consumer.
     */
    public JMSConnectionPool getConnectionPool() {
        return Optional.ofNullable(connectionPool)
                .orElseThrow(() -> new IllegalStateException("Connection Factory cannot be null"));
    }

    /***
     *
     * Set the {@link JMSConnectionPool} for the consumer.
     *
     * @param connectionPool
     */
    public void setConnectionPool(@Nullable JMSConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    /***
     * @return Returns the {@link Deserializer} configured for the consumer
     *      to convert an {@link Message} to an object.
     */
    public Deserializer getDeserializer() {
        return Optional.ofNullable(deserializer)
                .orElseThrow(() -> new IllegalStateException("Deserializer cannot be null"));
    }

    /***
     *
     * Sets the {@link Deserializer} for the consumer.
     *
     * @param deserializer
     */
    public void setDeserializer(@Nullable Deserializer deserializer) {
        this.deserializer = deserializer;
    }

    /***
     *
     * Receives a {@link Message} from the broker and and converts it
     *      to instance of type {@param <T>}.
     *
     * @param destination
     * @param clazz
     * @param <T>
     *
     * @return the message from the broker as an object instance of type {@param <T>}.
     *
     * @see io.micronaut.jms.serdes.DefaultSerializerDeserializer
     */
    public <T> T receive(@NotNull String destination, Class<T> clazz) {
        try (Connection connection = getConnectionPool().createConnection();
             Session session = connection.createSession(sessionTransacted, sessionAcknowledged)) {
            connection.start();
            return getDeserializer().deserialize(receive(session, lookupDestination(destination)), clazz);
        } catch (JMSException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Destination lookupDestination(String destination) {
        try (Connection connection = getConnectionPool().createConnection();
             Session session = connection.createSession(sessionTransacted, sessionAcknowledged)) {
            return type == JMSDestinationType.QUEUE ?
                    session.createQueue(destination) :
                    session.createTopic(destination);
        } catch (JMSException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    private Message receive(@NotNull Session session, @NotNull Destination destination) {
        try (MessageConsumer consumer = session.createConsumer(destination)) {
            Message message = receive(consumer);
            if (sessionTransacted) {
                session.commit();
            }
            if (session.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE) {
                if (message != null) {
                    message.acknowledge();
                }
            }
            return message;
        } catch (JMSException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Message receive(@NotNull MessageConsumer consumer) throws JMSException {
        return consumer.receive();
    }
}
