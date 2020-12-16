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

import javax.jms.BytesMessage;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;
import java.io.Serializable;
import java.util.Map;

/***
 * Enumeration of the underlying types of a {@link Message} as well as a mapping to the corresponding Java class.
 *
 * @see io.micronaut.jms.serdes.DefaultSerializerDeserializer
 * @see Message
 * @see TextMessage
 *
 * @author elliottpope
 * @since 1.0
 */
public enum MessageType {
    /***
     * Corresponds to a {@link TextMessage} to be deserialized to a {@link String}.
     */
    TEXT(TextMessage.class, String.class),

    /***
     * Corresponds to a {@link MapMessage} to be deserialized to a {@link Map}.
     */
    MAP(MapMessage.class, Map.class),

    /***
     * Corresponds to a {@link ObjectMessage} to be deserialized to a {@link Serializable}.
     */
    OBJECT(ObjectMessage.class, Serializable.class),

    /***
     * Corresponds to a {@link BytesMessage} to be deserialized to a byte array.
     */
    BYTES(BytesMessage.class, byte[].class),

    /***
     * The default case if no known {@link Message} subtype is known for the incoming message.
     */
    UNKNOWN(null, null);

    private final Class<? extends Message> toClazz;
    private final Class<?> fromClazz;

    MessageType(Class<? extends Message> toClazz,
                Class<?> fromClazz) {
        this.toClazz = toClazz;
        this.fromClazz = fromClazz;
    }

    /***
     * @param message - the {@link Message} whose type you would like to infer.
     * @return the {@link MessageType} that corresponds to the given {@param message}. If the {@param message}
     *      is null or the type is not supported, then {@link MessageType#UNKNOWN} will be returned.
     */
    public static @NonNull MessageType fromMessage(@Nullable Message message) {
        if (message != null) {
            for (MessageType type : MessageType.values()) {
                if (type.toClazz.isAssignableFrom(message.getClass())) {
                    return type;
                }
            }
        }
        return UNKNOWN;
    }

    /***
     * @param message - the {@link Message} whose type you would like to infer.
     * @return the {@link MessageType} that corresponds to the given {@param message}. If the {@param message}
     *      is null or the type is not supported, then {@link MessageType#UNKNOWN} will be returned.
     */
    public static MessageType fromObject(Object message) {
        if (message != null) {
            for (MessageType type : MessageType.values()) {
                if (type.fromClazz.isAssignableFrom(message.getClass())) {
                    return type;
                }
            }
        }
        return UNKNOWN;
    }
}
