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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class DefaultSerializerDeserializer implements Serializer<Object>, Deserializer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSerializerDeserializer.class);

    @Override
    public <T> T deserialize(Message message, Class<T> clazz) {
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
                    return (T) output;
                case TEXT:
                    final TextMessage textMessage = (TextMessage) message;
                    if (clazz.isAssignableFrom(String.class)) {
                        return (T) textMessage.getText();
                    }
                    return OBJECT_MAPPER.readValue(textMessage.getText(), clazz);
                case BYTES:
                    final BytesMessage bytesMessage = (BytesMessage) message;
                    byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
                    bytesMessage.readBytes(bytes);
                    return (T) bytes;
                case OBJECT:
                    final ObjectMessage objectMessage = (ObjectMessage) message;
                    return (T) objectMessage.getObject();
                default:
                    throw new IllegalArgumentException("No known deserialization of message " + message);
            }
        } catch (JMSException | JsonProcessingException e) {
            LOGGER.error("Failed to deserialize message " + message + " due to an error.", e);
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
                    return session.createTextMessage(OBJECT_MAPPER.writeValueAsString(input));
                default:
                    throw new IllegalArgumentException("No known serialization of message " + input);
            }
        } catch (JMSException | JsonProcessingException e) {
            LOGGER.error("Failed to serialize input " + input + " due to an error.", e);
        }
        throw new IllegalArgumentException("Failed to serialize input " + input);
    }
}

