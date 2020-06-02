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
