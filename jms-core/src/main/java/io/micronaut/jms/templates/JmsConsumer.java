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
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.serdes.Deserializer;
import io.micronaut.messaging.exceptions.MessageListenerException;
import io.micronaut.messaging.exceptions.MessagingSystemException;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;
import static javax.jms.Session.AUTO_ACKNOWLEDGE;
import static javax.jms.Session.CLIENT_ACKNOWLEDGE;

/**
 * Helper class that receives messages, configuring JMS connections, sessions,
 * etc. for you.
 *
 * TODO rename with JmsProducer
 * @author Elliott Pope
 * @since 1.0.0
 */
public class JmsConsumer {

    private final JMSDestinationType type;
    private final JMSConnectionPool connectionPool;
    private final Deserializer deserializer;
    private final boolean sessionTransacted;
    private final int sessionAcknowledgeMode;

    public JmsConsumer(JMSDestinationType type,
                       JMSConnectionPool connectionPool,
                       Deserializer deserializer) {
        this(type, connectionPool, deserializer, false, AUTO_ACKNOWLEDGE);
    }

    public JmsConsumer(JMSDestinationType type,
                       JMSConnectionPool connectionPool,
                       Deserializer deserializer,
                       boolean sessionTransacted,
                       int sessionAcknowledgeMode) {
        this.type = type;
        this.connectionPool = connectionPool;
        this.deserializer = deserializer;
        this.sessionTransacted = sessionTransacted;
        this.sessionAcknowledgeMode = sessionAcknowledgeMode;
    }

    /**
     * Receives a {@link Message} from the broker and and converts it to
     * an instance of type {@code <T>}.
     *
     * @param destination the queue or topic name
     * @param clazz       the class
     * @param <T>         the class type
     * @return the message from the broker as an object instance of type {@code <T>}.
     */
    public <T> T receive(@NonNull String destination,
                         Class<T> clazz) {
        try (Connection connection = createConnection();
             Session session = createSession(connection)) {
            connection.start();
            return deserializer.deserialize(receive(session, lookupDestination(destination)), clazz);
        } catch (JMSException | RuntimeException e) {
            throw new MessageListenerException("Problem receiving message", e);
        }
    }

    /**
     * Receives a {@link Message} from the broker.
     *
     * @param destination the queue or topic name
     * @return the message
     */
    public Message receive(@NonNull String destination) {
        try (Connection connection = createConnection();
             Session session = createSession(connection)) {
            connection.start();
            return receive(session, lookupDestination(destination));
        } catch (JMSException | RuntimeException e) {
            throw new MessageListenerException("Problem receiving message", e);
        }
    }

    @Override
    public String toString() {
        return "JmsConsumer{" +
            "type=" + type +
            ", connectionPool=" + connectionPool +
            ", deserializer=" + deserializer +
            ", sessionTransacted=" + sessionTransacted +
            ", sessionAcknowledgeMode=" + sessionAcknowledgeMode +
            '}';
    }

    @Nullable
    private Message receive(@NonNull Session session,
                            @NonNull Destination destination) throws JMSException {
        try (MessageConsumer consumer = session.createConsumer(destination)) {
            Message message = consumer.receive();
            if (sessionTransacted) {
                session.commit();
            }
            if (message != null && session.getAcknowledgeMode() == CLIENT_ACKNOWLEDGE) {
                message.acknowledge();
            }
            return message;
        }
    }

    private Destination lookupDestination(String destination) {
        try (Connection connection = createConnection();
             Session session = createSession(connection)) {
            return type == QUEUE ?
                session.createQueue(destination) :
                session.createTopic(destination);
        } catch (JMSException | RuntimeException e) {
            throw new MessagingSystemException(
                "Problem looking up destination " + destination, e);
        }
    }

    private Session createSession(Connection connection) throws JMSException {
        return connection.createSession(sessionTransacted, sessionAcknowledgeMode);
    }

    private Connection createConnection() throws JMSException {
        return connectionPool.createConnection();
    }
}
