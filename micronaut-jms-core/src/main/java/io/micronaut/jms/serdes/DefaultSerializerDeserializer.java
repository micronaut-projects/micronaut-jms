package io.micronaut.jms.serdes;

import io.micronaut.jms.model.MessageType;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class DefaultSerializerDeserializer implements Serializer<Object>, Deserializer {
    @Override
    public Object deserialize(Message message) {
        if (message == null) {
            return null;
        }
        try {
            switch (MessageType.fromMessage(message)) {
                case MAP:
                    final MapMessage mapMessage = (MapMessage) message;
                    final Enumeration<String> keys = mapMessage.getMapNames();
                    final Map<String, Object> output = new HashMap<>();
                    while (keys.hasMoreElements()) {
                        final String key = keys.nextElement();
                        output.put(key, mapMessage.getObject(key));
                    }
                    return output;
                case TEXT:
                    final TextMessage textMessage = (TextMessage) message;
                    return textMessage.getText();
                case BYTES:
                    final BytesMessage bytesMessage = (BytesMessage) message;
                    byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
                    bytesMessage.readBytes(bytes);
                    return bytes;
                case OBJECT:
                    final ObjectMessage objectMessage = (ObjectMessage) message;
                    return objectMessage.getObject();
                default:
                    throw new IllegalArgumentException("No known deserialization of message " + message);
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
        throw new IllegalArgumentException("Failed to deserialize message " + message);
    }

    @Override
    public Message serialize(Session session, Object input) {
        try {
            switch (MessageType.fromObject(input)) {
                case MAP:
                    final MapMessage message = session.createMapMessage();
                    final Map<?, ?> inputMap = (Map<?, ?>) input;
                    for (Map.Entry<?, ?> entry : inputMap.entrySet()) {

                        if (!(entry.getKey() instanceof String)) {
                            throw new IllegalArgumentException(
                                    String.format("Failed to convert input due to key %s with type %s",
                                            entry.getKey(),
                                            entry.getKey().getClass()));
                        }
                        message.setObject((String) entry.getKey(), entry.getValue());
                    }
                    return message;
                case TEXT:
                    return session.createTextMessage((String) input);
                case BYTES:
                    final BytesMessage bytesMessage = session.createBytesMessage();
                    bytesMessage.readBytes((byte[]) input);
                    return bytesMessage;
                case OBJECT:
                    return session.createObjectMessage((Serializable) input);
                default:
                    throw new IllegalArgumentException("No known serialization of message " + input);
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
        throw new IllegalArgumentException("Failed to serialize input " + input);
    }
}

