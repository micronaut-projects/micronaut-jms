package io.micronaut.jms.docs.binding;

// tag::imports[]
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Message;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.messaging.annotation.MessageHeader;

import javax.jms.Destination;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
import static io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID;
import static io.micronaut.jms.model.JMSHeaders.JMS_DELIVERY_MODE;
import static io.micronaut.jms.model.JMSHeaders.JMS_DESTINATION;
import static io.micronaut.jms.model.JMSHeaders.JMS_EXPIRATION;
import static io.micronaut.jms.model.JMSHeaders.JMS_MESSAGE_ID;
import static io.micronaut.jms.model.JMSHeaders.JMS_PRIORITY;
import static io.micronaut.jms.model.JMSHeaders.JMS_REDELIVERED;
import static io.micronaut.jms.model.JMSHeaders.JMS_REPLY_TO;
import static io.micronaut.jms.model.JMSHeaders.JMS_TIMESTAMP;
import static io.micronaut.jms.model.JMSHeaders.JMS_TYPE;
// end::imports[]
import io.micronaut.context.annotation.Requires;

@Requires(property = "spec.name", value = "BindingSpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class MapConsumer {

    List<Map<String, Serializable>> messageBodies = Collections.synchronizedList(new ArrayList<>());
    List<Map<String, Object>> messageHeaders = Collections.synchronizedList(new ArrayList<>());
    List<javax.jms.Message> messages = Collections.synchronizedList(new ArrayList<>());

    @Queue(value = "queue_map", concurrency = "1-5")
    public void receive(@MessageBody Map<String, Serializable> body,
                        @Message javax.jms.Message message,
                        @MessageHeader(JMS_CORRELATION_ID) @Nullable String correlationId,
                        @MessageHeader(JMS_DELIVERY_MODE) int deliveryMode,
                        @MessageHeader(JMS_DESTINATION) Destination destination,
                        @MessageHeader(JMS_EXPIRATION) long expiration,
                        @MessageHeader(JMS_MESSAGE_ID) String messageId,
                        @MessageHeader(JMS_PRIORITY) int priority,
                        @MessageHeader(JMS_REDELIVERED) boolean redelivered,
                        @MessageHeader(JMS_REPLY_TO) @Nullable Destination replyTo,
                        @MessageHeader(JMS_TIMESTAMP) long timestamp,
                        @MessageHeader(JMS_TYPE) @Nullable String type,
                        @MessageHeader("CustomStringHeader") @Nullable String stringHeader,
                        @MessageHeader("CustomBooleanHeader") boolean booleanHeader,
                        @MessageHeader("CustomByteHeader") byte byteHeader,
                        @MessageHeader("CustomShortHeader") short shortHeader,
                        @MessageHeader("CustomIntegerHeader") int intHeader,
                        @MessageHeader("CustomLongHeader") long longHeader,
                        @MessageHeader("CustomFloatHeader") float floatHeader,
                        @MessageHeader("CustomDoubleHeader") double doubleHeader) {

        Map<String, Object> headerValues = new HashMap<>();
        headerValues.put(JMS_CORRELATION_ID, correlationId);
        headerValues.put(JMS_DELIVERY_MODE, deliveryMode);
        headerValues.put(JMS_DESTINATION, destination);
        headerValues.put(JMS_EXPIRATION, expiration);
        headerValues.put(JMS_MESSAGE_ID, messageId);
        headerValues.put(JMS_PRIORITY, priority);
        headerValues.put(JMS_REDELIVERED, redelivered);
        headerValues.put(JMS_REPLY_TO, replyTo);
        headerValues.put(JMS_TIMESTAMP, timestamp);
        headerValues.put(JMS_TYPE, type);
        headerValues.put("CustomStringHeader", stringHeader);
        headerValues.put("CustomBooleanHeader", booleanHeader);
        headerValues.put("CustomByteHeader", byteHeader);
        headerValues.put("CustomShortHeader", shortHeader);
        headerValues.put("CustomIntegerHeader", intHeader);
        headerValues.put("CustomLongHeader", longHeader);
        headerValues.put("CustomFloatHeader", floatHeader);
        headerValues.put("CustomDoubleHeader", doubleHeader);

        messageHeaders.add(headerValues);
        messageBodies.add(body);
        messages.add(message);
    }
}
// end::clazz[]
