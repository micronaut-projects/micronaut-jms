package io.micronaut.jms.docs.binding

import io.micronaut.jms.docs.AbstractJmsSpec

import static io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID

class BindingSpec extends AbstractJmsSpec {

    void 'test map producer and consumer with headers'() {
        when:

        int foo = 123
        boolean bar = true
        Map<String, Serializable> body = [:]
        body.foo = foo
        body.bar = bar

        Random random = new Random()
        String correlationId = UUID.randomUUID()
        String stringValue = UUID.randomUUID()
        boolean booleanValue = random.nextBoolean()
        byte byteValue = (byte) random.nextInt(Byte.MAX_VALUE)
        short shortValue = (short) random.nextInt(Short.MAX_VALUE)
        int intValue = random.nextInt()
        long longValue = System.currentTimeMillis()
        float floatValue = random.nextFloat()
        double doubleValue = random.nextDouble()

        MapProducer mapProducer = applicationContext.getBean(MapProducer)
        mapProducer.send(
            body,
            correlationId,
            stringValue,
            booleanValue,
            byteValue,
            shortValue,
            intValue,
            longValue,
            floatValue,
            doubleValue)

        MapConsumer mapConsumer = applicationContext.getBean(MapConsumer)

        then:
        polling.eventually {
            mapConsumer.messageBodies.size() == 1 &&
                    mapConsumer.messageBodies[0].foo == foo &&
                    mapConsumer.messageBodies[0].bar == bar &&
                    mapConsumer.messageHeaders.size() == 1 &&
                    mapConsumer.messageHeaders[0][JMS_CORRELATION_ID] == correlationId &&
                    mapConsumer.messageHeaders[0].CustomStringHeader == stringValue &&
                    mapConsumer.messageHeaders[0].CustomBooleanHeader == booleanValue &&
                    mapConsumer.messageHeaders[0].CustomByteHeader == byteValue &&
                    mapConsumer.messageHeaders[0].CustomShortHeader == shortValue &&
                    mapConsumer.messageHeaders[0].CustomIntegerHeader == intValue &&
                    mapConsumer.messageHeaders[0].CustomLongHeader == longValue &&
                    mapConsumer.messageHeaders[0].CustomFloatHeader == floatValue &&
                    mapConsumer.messageHeaders[0].CustomDoubleHeader == doubleValue
        }
    }
}
