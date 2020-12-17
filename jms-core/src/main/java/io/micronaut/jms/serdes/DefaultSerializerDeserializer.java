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
package io.micronaut.jms.serdes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.jms.model.MessageType;
import io.micronaut.messaging.exceptions.MessageListenerException;
import io.micronaut.messaging.exceptions.MessagingClientException;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of {@link Serializer} and {@link Deserializer}.
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
public final class DefaultSerializerDeserializer implements Serializer<Object>, Deserializer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DefaultSerializerDeserializer INSTANCE = new DefaultSerializerDeserializer();

    private DefaultSerializerDeserializer() {
    }

    public static DefaultSerializerDeserializer getInstance() {
        return INSTANCE;
    }

    @Override
    public <T> T deserialize(Message message,
                             Class<T> clazz) {
        if (message == null) {
            return null;
        }

        try {
            switch (MessageType.fromMessage(message)) {
                case MAP:
                    return deserializeMap((MapMessage) message);
                case TEXT:
                    return deserializeText((TextMessage) message, clazz);
                case BYTES:
                    return deserializeBytes((BytesMessage) message);
                case OBJECT:
                    return deserializeObject((ObjectMessage) message);
                default:
                    throw new IllegalArgumentException("No known deserialization of message " + message);
            }
        } catch (JMSException | JsonProcessingException | RuntimeException e) {
            throw new MessageListenerException("Problem deserializing message " + message, e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeMap(final MapMessage message) throws JMSException {
        final Enumeration<String> keys = message.getMapNames();
        final Map<String, Object> output = new HashMap<>();
        while (keys.hasMoreElements()) {
            final String key = keys.nextElement();
            output.put(key, message.getObject(key));
        }
        return (T) output;
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeText(final TextMessage message,
                                  final Class<T> clazz) throws JMSException, JsonProcessingException {
        if (clazz.isAssignableFrom(String.class)) {
            return (T) message.getText();
        }
        return OBJECT_MAPPER.readValue(message.getText(), clazz);
    }

    private <T> T deserializeBytes(final BytesMessage message) throws JMSException {
        byte[] bytes = new byte[(int) message.getBodyLength()];
        message.readBytes(bytes);
        return (T) bytes;
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeObject(final ObjectMessage message) throws JMSException {
        return (T) message.getObject();
    }

    @Override
    public Message serialize(Session session,
                             Object input) {
        try {
            switch (MessageType.fromObject(input)) {
                case MAP:
                    return serializeMap(session, (Map<?, ?>) input);
                case TEXT:
                    return serializeText(session, (String) input);
                case BYTES:
                    return serializeBytes(session, (byte[]) input);
                case OBJECT:
                    return serializeObject(session, input);
                default:
                    throw new IllegalArgumentException("No known serialization of message " + input);
            }
        } catch (JMSException | JsonProcessingException | RuntimeException e) {
            throw new MessagingClientException("Problem serializing input " + input, e);
        }
    }

    private MapMessage serializeMap(final Session session,
                                    final Map<?, ?> input) throws JMSException {
        final MapMessage message = session.createMapMessage();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (!(entry.getKey() instanceof CharSequence)) {
                throw new IllegalArgumentException(
                    "Invalid MapMessage key type " +
                        entry.getKey().getClass().getName() +
                        "; must be a String/CharSequence");
            }
            message.setObject(((CharSequence) entry.getKey()).toString(), entry.getValue());
        }
        return message;
    }

    private TextMessage serializeText(final Session session,
                                      final String input) throws JMSException {
        return session.createTextMessage(input);
    }

    private BytesMessage serializeBytes(final Session session,
                                        final byte[] input) throws JMSException {
        final BytesMessage message = session.createBytesMessage();
        message.readBytes(input);
        return message;
    }

    private TextMessage serializeObject(final Session session,
                                        final Object input) throws JMSException, JsonProcessingException {
        // TODO should be ObjectMessage
        return session.createTextMessage(OBJECT_MAPPER.writeValueAsString(input));
    }
}
