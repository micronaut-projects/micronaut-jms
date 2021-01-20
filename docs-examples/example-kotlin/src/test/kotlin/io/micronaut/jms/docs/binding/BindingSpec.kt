package io.micronaut.jms.docs.binding

import io.kotest.assertions.timing.eventually
import io.micronaut.jms.docs.AbstractJmsKotest
import io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID
import org.opentest4j.AssertionFailedError
import java.util.Random
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
class BindingSpec : AbstractJmsKotest({

    val specName = javaClass.simpleName

    given("A Map producer and consumer with headers") {
        val applicationContext = startContext(specName)

        `when`("the message is published") {
            val foo = 123
            val bar = true
            val body = mapOf("foo" to foo, "bar" to bar)

            val random = Random()
            val correlationId = UUID.randomUUID().toString()
            val stringValue = UUID.randomUUID().toString()
            val booleanValue = random.nextBoolean()
            val byteValue = random.nextInt(Byte.MAX_VALUE.toInt()).toByte()
            val shortValue = random.nextInt(Short.MAX_VALUE.toInt()).toShort()
            val intValue = random.nextInt()
            val longValue = System.currentTimeMillis()
            val floatValue = random.nextFloat()
            val doubleValue = random.nextDouble()

            val mapProducer = applicationContext.getBean(MapProducer::class.java)
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
                doubleValue
            )

            val mapConsumer = applicationContext.getBean(MapConsumer::class.java)

            then("the message is consumed") {
                eventually(3.seconds, AssertionFailedError::class) {
                    mapConsumer.messageBodies.size == 1 &&
                        mapConsumer.messageBodies[0]["foo"] == foo &&
                        mapConsumer.messageBodies[0]["bar"] == bar &&
                        mapConsumer.messageHeaders.size == 1 &&
                        mapConsumer.messageHeaders[0][JMS_CORRELATION_ID] == correlationId &&
                        mapConsumer.messageHeaders[0]["CustomStringHeader"] == stringValue &&
                        mapConsumer.messageHeaders[0]["CustomBooleanHeader"] == booleanValue &&
                        mapConsumer.messageHeaders[0]["CustomByteHeader"] == byteValue &&
                        mapConsumer.messageHeaders[0]["CustomShortHeader"] == shortValue &&
                        mapConsumer.messageHeaders[0]["CustomIntegerHeader"] == intValue &&
                        mapConsumer.messageHeaders[0]["CustomLongHeader"] == longValue &&
                        mapConsumer.messageHeaders[0]["CustomFloatHeader"] == floatValue &&
                        mapConsumer.messageHeaders[0]["CustomDoubleHeader"] == doubleValue
                }
            }
        }

        applicationContext.stop()
    }
})
