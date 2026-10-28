package io.micronaut.jms.docs.serdes;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.serdes.Deserializer;
import io.micronaut.jms.serdes.Serializer;
import io.micronaut.messaging.exceptions.MessagingException;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import java.util.Arrays;
import java.util.List;
// end::imports[]

@Requires(property = "spec.name", value = "SerdesSpec")
// tag::clazz[]
@Singleton
@Named("csv") // <1>
public class CsvSerdes implements Serializer, Deserializer {

    @Override
    @SuppressWarnings("unchecked")
    public Message serialize(Session session, Object body) {
        try {
            return session.createTextMessage(String.join(",", (List<String>) body));
        } catch (JMSException e) {
            throw new MessagingException("Unable to create the message", e);
        }
    }

    @Override
    public <T> T deserialize(Message message, Class<T> type) {
        try {
            return type.cast(Arrays.asList(((TextMessage) message).getText().split(",")));
        } catch (JMSException e) {
            throw new MessagingException("Unable to read the message", e);
        }
    }
}
// end::clazz[]
