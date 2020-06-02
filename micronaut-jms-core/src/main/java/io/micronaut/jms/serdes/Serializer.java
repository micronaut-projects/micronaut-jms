package io.micronaut.jms.serdes;

import javax.jms.Message;
import javax.jms.Session;

public interface Serializer<T> {
    Message serialize(Session session, T input);
}
