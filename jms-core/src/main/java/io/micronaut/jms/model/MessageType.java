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
package io.micronaut.jms.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.BytesMessage;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.io.Serializable;
import java.util.Map;

/**
 * The underlying type of a {@link Message} mapped to the corresponding class.
 *
 * @author Elliott Pope
 * @see io.micronaut.jms.serdes.Deserializer
 * @see io.micronaut.jms.serdes.Serializer
 * @see Message
 * @see TextMessage
 * @since 1.0.0
 */
public enum MessageType {

    /**
     * A {@link TextMessage} to be deserialized to a {@link String}.
     */
    TEXT(TextMessage.class, String.class),

    /**
     * A {@link MapMessage} to be deserialized to a {@link Map}.
     */
    MAP(MapMessage.class, Map.class),

    /**
     * An {@link ObjectMessage} to be deserialized to a {@link Serializable}.
     */
    OBJECT(ObjectMessage.class, Serializable.class),

    /**
     * A {@link BytesMessage} to be deserialized to a byte array.
     */
    BYTES(BytesMessage.class, byte[].class),

    /**
     * A {@link StreamMessage}. JMS 2.0 only. Cannot deserialize, must be done
     * by the client.
     */
    STREAM(StreamMessage.class, null),

    /**
     * The default case if no known {@link Message} subtype is known for the incoming message.
     */
    UNKNOWN(null, null);

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageType.class);

    private final Class<? extends Message> toClazz;
    private final Class<?> fromClazz;

    MessageType(Class<? extends Message> toClazz,
                Class<?> fromClazz) {
        this.toClazz = toClazz;
        this.fromClazz = fromClazz;
    }

    /**
     * Determine the {@link MessageType} corresponding to the given
     * {@code message}. Returns {@link MessageType#UNKNOWN} if the
     * {@code message} is null or the type is not supported.
     *
     * @param message the {@link Message} whose type you would like to infer.
     * @return the {@link MessageType}
     */
    public static @NonNull MessageType fromMessage(@Nullable Message message) {
        if (message != null) {
            for (MessageType type : MessageType.values()) {
                if (type.toClazz != null && type.toClazz.isAssignableFrom(message.getClass())) {
                    return type;
                }
            }
        }
        LOGGER.warn("Unsupported Message type {}", message);
        return UNKNOWN;
    }

    /**
     * Determine the {@link MessageType} corresponding to the given
     * {@code message}. Returns {@link MessageType#UNKNOWN} if the
     * {@code message} is null or the type is not supported
     *
     * @param message the {@link Message} whose type you would like to infer.
     * @return the {@link MessageType}
     */
    public static MessageType fromObject(Object message) {
        if (message != null) {
            for (MessageType type : MessageType.values()) {
                if (type.fromClazz != null && type.fromClazz.isAssignableFrom(message.getClass())) {
                    return type;
                }
            }
        }
        LOGGER.warn("Unsupported object type {}", message);
        return UNKNOWN;
    }
}
