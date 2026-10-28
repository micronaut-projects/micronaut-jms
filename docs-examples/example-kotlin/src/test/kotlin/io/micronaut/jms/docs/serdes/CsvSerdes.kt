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

@Requires(property = "spec.name", value = "SerdesSpec")
// tag::clazz[]
@Singleton
@Named("csv") // <1>
class CsvSerdes : Serializer, Deserializer {

    override fun serialize(session: Session, body: Any): Message =
        session.createTextMessage((body as List<*>).joinToString(","))

    override fun <T> deserialize(message: Message, type: Class<T>): T =
        type.cast((message as TextMessage).text.split(","))
}
// end::clazz[]
