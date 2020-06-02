package io.micronaut.jms.templates;

import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.model.MessageHeader;
import io.micronaut.jms.serdes.Serializer;

import javax.annotation.Nullable;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Optional;

public class JmsProducer {
    @Nullable
    private ConnectionFactory connectionFactory;
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
     * @return the {@link ConnectionFactory} configured for the producer.
     */
    public ConnectionFactory getConnectionFactory() {
        return Optional.ofNullable(connectionFactory).orElseThrow(
                () -> new IllegalStateException("Connection Factory cannot be null"));
    }

    /***
     *
     * Sets the {@link ConnectionFactory} to be used by the producer.
     *
     * @param connectionFactory
     */
    public void setConnectionFactory(@Nullable ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
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
        try (Connection connection = getConnectionFactory().createConnection();
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
        try (Connection connection = getConnectionFactory().createConnection();
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
        try (Connection connection = getConnectionFactory().createConnection();
             Session session = getOrCreateSession(connection)) {
            send(session, destination, message, headers);
        } catch (JMSException e) {
            System.err.println("Exception occurred while sending message " + e);
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
            e.printStackTrace();
        }

    }

    private void send(@NotNull MessageProducer producer, @NotNull Message message, MessageHeader... headers) throws JMSException {
        notNull(producer, "MessageProducer cannot be null");
        notNull(message, "Message cannot be null");
        setJMSHeaders(message, headers);
        producer.send(message, Message.DEFAULT_DELIVERY_MODE, message.getJMSPriority(), Message.DEFAULT_TIME_TO_LIVE);
    }

    private static void notNull(Object object, String failureMessage) {
        Optional.ofNullable(object)
                .orElseThrow(() -> new IllegalStateException(failureMessage));
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
