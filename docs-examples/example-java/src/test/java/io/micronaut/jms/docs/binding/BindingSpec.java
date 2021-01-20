package io.micronaut.jms.docs.binding;

import io.micronaut.jms.docs.AbstractJmsSpec;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

class BindingSpec extends AbstractJmsSpec {

    @Test
    void testMapProducerAndConsumerWithHeaders() {
        int foo = 123;
        boolean bar = true;
        Map<String, Serializable> body = new HashMap<>();
        body.put("foo", foo);
        body.put("bar", bar);

        Random random = new Random();
        String correlationId = UUID.randomUUID().toString();
        String stringValue = UUID.randomUUID().toString();
        boolean booleanValue = random.nextBoolean();
        byte byteValue = (byte) random.nextInt(Byte.MAX_VALUE);
        short shortValue = (short) random.nextInt(Short.MAX_VALUE);
        int intValue = random.nextInt();
        long longValue = System.currentTimeMillis();
        float floatValue = random.nextFloat();
        double doubleValue = random.nextDouble();

        MapProducer mapProducer = applicationContext.getBean(MapProducer.class);
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
            doubleValue);

        MapConsumer mapConsumer = applicationContext.getBean(MapConsumer.class);
        await().atMost(3, SECONDS).until(() ->
            mapConsumer.messageBodies.size() == 1 &&
                mapConsumer.messageBodies.get(0).get("foo").equals(foo) &&
                mapConsumer.messageBodies.get(0).get("bar").equals(bar) &&
                mapConsumer.messageHeaders.size() == 1 &&
                mapConsumer.messageHeaders.get(0).get(JMS_CORRELATION_ID).equals(correlationId) &&
                mapConsumer.messageHeaders.get(0).get("CustomStringHeader").equals(stringValue) &&
                mapConsumer.messageHeaders.get(0).get("CustomBooleanHeader").equals(booleanValue) &&
                mapConsumer.messageHeaders.get(0).get("CustomByteHeader").equals(byteValue) &&
                mapConsumer.messageHeaders.get(0).get("CustomShortHeader").equals(shortValue) &&
                mapConsumer.messageHeaders.get(0).get("CustomIntegerHeader").equals(intValue) &&
                mapConsumer.messageHeaders.get(0).get("CustomLongHeader").equals(longValue) &&
                mapConsumer.messageHeaders.get(0).get("CustomFloatHeader").equals(floatValue) &&
                mapConsumer.messageHeaders.get(0).get("CustomDoubleHeader").equals(doubleValue)
        );
    }
}
