package io.micronaut.jms.docs.serdes

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.jms.serdes.Deserializer
import io.micronaut.jms.serdes.Serializer
import jakarta.inject.Named
import jakarta.inject.Singleton
import jakarta.jms.Message
import jakarta.jms.Session
import jakarta.jms.TextMessage
// end::imports[]

@Requires(property = 'spec.name', value = 'SerdesSpec')
// tag::clazz[]
@Singleton
@Named('csv') // <1>
class CsvSerdes implements Serializer, Deserializer {

    @Override
    Message serialize(Session session, Object body) {
        session.createTextMessage(((List<String>) body).join(','))
    }

    @Override
    <T> T deserialize(Message message, Class<T> type) {
        type.cast(((TextMessage) message).text.split(',').toList())
    }
}
// end::clazz[]
