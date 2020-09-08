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

import javax.jms.BytesMessage;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;
import java.io.Serializable;
import java.util.Map;

public enum MessageType {
    TEXT(TextMessage.class, String.class),
    MAP(MapMessage.class, Map.class),
    OBJECT(ObjectMessage.class, Serializable.class),
    BYTES(BytesMessage.class, byte[].class),
    UNKNOWN(null, null);

    private Class<? extends Message> toClazz;
    private Class<?> fromClazz;

    MessageType(Class<? extends Message> toClazz, Class<?> fromClazz) {
        this.toClazz = toClazz;
        this.fromClazz = fromClazz;
    }

    public static MessageType fromMessage(Message message) {
        if (message == null) {
            return UNKNOWN;
        }
        for (MessageType type : MessageType.values()) {
            if (type.toClazz.isAssignableFrom(message.getClass())) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public static MessageType fromObject(Object message) {
        for (MessageType type : MessageType.values()) {
            if (type.fromClazz.isAssignableFrom(message.getClass())) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
