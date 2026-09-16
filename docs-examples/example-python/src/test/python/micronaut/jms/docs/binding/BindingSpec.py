import random
import struct
import time
import uuid
from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property
from micronaut.inject.qualifiers import Qualifiers
from micronaut.jms.docs.Await import Await
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from .MapConsumer import MapConsumer
from .MapProducer import MapProducer

# TODO(python): java.type needed because the imported jakarta.jms.ConnectionFactory interface is not a Java class
# at runtime (containsBean(ConnectionFactory) fails with "Unsupported operation identifier 'typeHashCode'")
ConnectionFactory = java.type("jakarta.jms.ConnectionFactory")


@MicronautTest
@Property(name="spec.name", value="BindingSpec")
@Property(name="micronaut.jms.activemq.classic.connection-string", value="vm://BindingSpec?broker.persistent=false")
@Property(name="micronaut.jms.activemq.classic.enabled", value="true")
@Property(name="micronaut.jms.activemq.classic.username", value="activemq")
@Property(name="micronaut.jms.activemq.classic.password", value="activemq")
class BindingSpec:
    application_context: Annotated[ApplicationContext, Inject]
    map_producer: Annotated[MapProducer, Inject]
    map_consumer: Annotated[MapConsumer, Inject]

    @Test
    def test_map_producer_and_consumer_with_headers(self) -> None:
        assert self.application_context.containsBean(ConnectionFactory)
        assert self.application_context.containsBean(ConnectionFactory, Qualifiers.byName("activeMqConnectionFactory"))

        foo = 123
        bar = True
        body = {"foo": foo, "bar": bar}

        correlation_id = str(uuid.uuid4())
        string_value = str(uuid.uuid4())
        boolean_value = random.choice([True, False])
        byte_value = random.randint(0, 127)
        short_value = random.randint(0, 32767)
        int_value = random.randint(-2**31, 2**31 - 1)
        long_value = int(time.time() * 1000)
        # a value that survives the round trip through a 32-bit Java float
        float_value = struct.unpack("f", struct.pack("f", random.random()))[0]
        double_value = random.random()

        self.map_producer.send(
            body,
            correlation_id,
            string_value,
            boolean_value,
            byte_value,
            short_value,
            int_value,
            long_value,
            float_value,
            double_value)

        message_bodies = self.map_consumer.message_bodies
        message_headers = self.map_consumer.message_headers
        Await.until(lambda: len(message_bodies) == 1
                    and message_bodies[0].get("foo") == foo
                    and message_bodies[0].get("bar") == bar
                    and len(message_headers) == 1
                    and message_headers[0]["JMSCorrelationID"] == correlation_id
                    and message_headers[0]["CustomStringHeader"] == string_value
                    and message_headers[0]["CustomBooleanHeader"] == boolean_value
                    and message_headers[0]["CustomByteHeader"] == byte_value
                    and message_headers[0]["CustomShortHeader"] == short_value
                    and message_headers[0]["CustomIntegerHeader"] == int_value
                    and message_headers[0]["CustomLongHeader"] == long_value
                    and message_headers[0]["CustomFloatHeader"] == float_value
                    and message_headers[0]["CustomDoubleHeader"] == double_value)
