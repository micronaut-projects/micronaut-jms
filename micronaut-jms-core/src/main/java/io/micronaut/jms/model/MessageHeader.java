package io.micronaut.jms.model;

import javax.jms.JMSException;
import javax.jms.Message;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class MessageHeader {
    private static final Map<String, BiConsumer<Message, String>> JMS_HEADERS = new HashMap<>();

    private String key;
    private String value;
    private boolean isJMSHeader;

    private MessageHeader() {
        JMS_HEADERS.put("JMSCorrelationID", (message, value) -> {
            try {
                message.setJMSCorrelationID(value);
            } catch (JMSException e) {
                // log error
            }
        });
        JMS_HEADERS.put("JMSMessageID", (message, value) -> {
            try {
                message.setJMSMessageID(value);
            } catch (JMSException e) {
                // log error
            }
        });
        JMS_HEADERS.put("JMSType", (message, value) -> {
            try {
                message.setJMSType(value);
            } catch (JMSException e) {
                // log error
            }
        });
        JMS_HEADERS.put("JMSDeliveryTime", (message, value) -> {
            try {
                message.setJMSDeliveryTime(LocalDateTime.parse(value)
                        .toEpochSecond(ZoneOffset.UTC));
            } catch (JMSException e) {
                // log error
            }
        });
    }

    public MessageHeader(String key, String value) {
        this();
        this.key = key;
        this.value = value;
        this.isJMSHeader = JMS_HEADERS.containsKey(key);
    }

    /***
     *
     * @return true if the {@param key} is a JMS Header. Returns false if not.
     */
    public boolean isJMSHeader() {
        return isJMSHeader;
    }

    /***
     *
     * Attempts to set the JMS Header given by the {@param key}
     *      with the value given with {@param value} doing all required
     *      type conversions. Not all JMS Headers are supported since they
     *      are not managed at the message level (i.e. {@link JMSHeaders#JMS_PRIORITY})
     *
     * @param message
     */
    public void setJMSHeader(Message message) {
        JMS_HEADERS.get(key).accept(message, value);
    }

    /***
     * Attempts to set a {@link String} property on the provided
     *      {@link Message}.
     *
     * @param message
     */
    public void setHeader(Message message) {
        try {
            message.setStringProperty(key, value);
        } catch (JMSException e) {
            // log error
        }
    }
}
