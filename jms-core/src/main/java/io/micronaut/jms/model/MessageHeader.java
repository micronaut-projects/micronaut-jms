/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jms.model;

import javax.jms.JMSException;
import javax.jms.Message;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID;
import static io.micronaut.jms.model.JMSHeaders.JMS_TYPE;

/***
 * Container class to correspond to a header on a JMS message. Used in the {@link io.micronaut.jms.templates.JmsProducer}
 *      and the JMS implementation for {@link io.micronaut.messaging.annotation.Header}.
 *
 * @see io.micronaut.jms.templates.JmsProducer
 * @see io.micronaut.jms.configuration.JMSProducerMethodInterceptor
 *
 * @author elliottpope
 * @since 1.0
 */
public class MessageHeader {

    private static final Map<String, BiConsumer<Message, String>> JMS_HEADERS = new HashMap<>();

    static {
        JMS_HEADERS.put(JMS_CORRELATION_ID, (message, value) -> {
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
        JMS_HEADERS.put(JMS_TYPE, (message, value) -> {
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

    private final String key;
    private final String value;
    private final boolean isJMSHeader;

    /***
     * Creates a container for the message header.
     *
     * @param key - the name of the header.
     * @param value - the value for the header.
     */
    public MessageHeader(String key, String value) {
        this.key = key;
        this.value = value;
        isJMSHeader = JMS_HEADERS.containsKey(key);
    }

    /***
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
