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

import io.micronaut.context.BeanLocator;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.jms.model.MessageType;
import io.micronaut.json.JsonMapper;
import io.micronaut.messaging.exceptions.MessageListenerException;
import io.micronaut.messaging.exceptions.MessagingClientException;
import jakarta.inject.Singleton;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Default implementation of {@link Serializer} and {@link Deserializer}.
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
@Singleton
public final class DefaultSerializerDeserializer implements Serializer, Deserializer {

    private final Supplier<JsonMapper> objectMapperSupplier;

    public DefaultSerializerDeserializer(BeanLocator beanLocator) {
        // Lazy load object mapper
        objectMapperSupplier = SupplierUtil.memoized(() -> beanLocator.getBean(JsonMapper.class));
    }

    @Override
    public <T> T deserialize(Message message, Class<T> clazz) {
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
                    return deserializeObject((ObjectMessage) message, clazz);
                default:
                    throw new IllegalArgumentException("No known deserialization of message " + message);
            }
        } catch (Exception e) {
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
                                  final Class<T> clazz) throws JMSException, IOException {
        if (clazz.isAssignableFrom(String.class)) {
            return (T) message.getText();
        }
        return objectMapperSupplier.get().readValue(message.getText(), Argument.of(clazz));
    }

    private <T> T deserializeBytes(final BytesMessage message) throws JMSException {
        byte[] bytes = new byte[(int) message.getBodyLength()];
        message.readBytes(bytes);
        return (T) bytes;
    }

    @SuppressWarnings("unchecked")
    private <T> T deserializeObject(final ObjectMessage message,
                                    final Class<T> clazz) throws JMSException, IOException {

        Serializable body = message.getObject();
        if (body instanceof String) {
            // if it's a String and the client asks for String, return that
            if (clazz.isAssignableFrom(String.class)) {
                return (T) body;
            }
        }
        return (T) message.getObject();
    }

    @Override
    public Message serialize(Session session, Object body) {
        try {
            switch (MessageType.fromObject(body)) {
                case MAP:
                    return serializeMap(session, (Map<?, ?>) body);
                case TEXT:
                    return serializeText(session, (String) body);
                case BYTES:
                    return serializeBytes(session, (byte[]) body);
                case OBJECT:
                    if (body instanceof Serializable) {
                        return serializeObject(session, (Serializable) body);
                    } else {
                        return serializeText(session, new String(objectMapperSupplier.get().writeValueAsBytes(body), StandardCharsets.UTF_8));
                    }
                case STREAM:
                    return serializeStream(session, (Object[]) body);
                default:
                    throw new IllegalArgumentException("No known serialization of message " + body);
            }
        } catch (Exception e) {
            throw new MessagingClientException("Problem serializing body " + body, e);
        }
    }

    private MapMessage serializeMap(final Session session,
                                    final Map<?, ?> body) throws JMSException {
        final MapMessage message = session.createMapMessage();
        for (Map.Entry<?, ?> entry : body.entrySet()) {
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
                                      final String body) throws JMSException {
        return session.createTextMessage(body);
    }

    private BytesMessage serializeBytes(final Session session,
                                        final byte[] body) throws JMSException {
        final BytesMessage message = session.createBytesMessage();
        message.readBytes(body);
        return message;
    }

    private ObjectMessage serializeObject(final Session session,
                                          final Serializable body) throws JMSException {
        return session.createObjectMessage(body);
    }

    private StreamMessage serializeStream(final Session session,
                                          final Object[] body) throws JMSException {
        StreamMessage message = session.createStreamMessage();
        for (Object o : body) {
            message.writeObject(o);
        }
        return message;
    }
}
