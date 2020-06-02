package io.micronaut.jms.serdes;

import javax.jms.Message;

public interface Deserializer {
    Object deserialize(Message message);
}
