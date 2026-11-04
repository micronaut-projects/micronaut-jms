# tag::imports[]
from jakarta.inject import Named, Singleton
from jakarta.jms import Message, Session
from java.util import Arrays
from micronaut.context.annotation import Requires
from micronaut.jms.serdes import Deserializer, Serializer
# end::imports[]


@Requires(property="spec.name", value="SerdesSpec")
# tag::clazz[]
@Singleton
@Named("csv")  # <1>
class CsvSerdes(Serializer, Deserializer):

    def serialize(self, session: Session, body: object) -> Message:
        return session.createTextMessage(",".join(body))

    def deserialize(self, message: Message, type: type) -> object:
        return Arrays.asList(*message.getText().split(","))
# end::clazz[]
