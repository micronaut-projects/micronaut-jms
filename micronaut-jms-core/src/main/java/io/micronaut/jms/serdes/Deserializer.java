package io.micronaut.jms.serdes;

import javax.jms.Message;

public interface Deserializer {
    default Object deserialize(Message message) {
        return deserialize(message, Object.class);
    }

    <T> T deserialize(Message message, Class<T> clazz);
}
