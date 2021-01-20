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

import io.micronaut.messaging.exceptions.MessagingClientException;
import io.micronaut.messaging.exceptions.MessagingSystemException;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.micronaut.jms.model.JMSHeaders.JMS_CORRELATION_ID;
import static io.micronaut.jms.model.JMSHeaders.JMS_REPLY_TO;
import static io.micronaut.jms.model.JMSHeaders.JMS_TYPE;
import static io.micronaut.jms.util.HeaderNameUtils.encode;

/**
 * Represents a header on a JMS message. Used in the {@link io.micronaut.jms.templates.JmsProducer}
 * and the JMS implementation for {@link io.micronaut.messaging.annotation.Header}.
 *
 * @author Elliott Pope
 * @see io.micronaut.jms.templates.JmsProducer
 * @see io.micronaut.jms.configuration.JMSProducerMethodInterceptor
 * @since 1.0.0
 */
public class MessageHeader {

    private static final Map<String, BiConsumer<Message, Object>> JMS_HEADER_OPERATIONS = new HashMap<>();

    static {
        JMS_HEADER_OPERATIONS.put(JMS_CORRELATION_ID, (message, value) -> {
            checkArgumentType(value, String.class);
            try {
                message.setJMSCorrelationID((String) value);
            } catch (JMSException | RuntimeException e) {
                throw new MessagingSystemException("Problem setting JMSCorrelationID header", e);
            }
        });
        JMS_HEADER_OPERATIONS.put(JMS_REPLY_TO, (message, value) -> {
            checkArgumentType(value, Destination.class);
            try {
                message.setJMSReplyTo((Destination) value);
            } catch (JMSException | RuntimeException e) {
                throw new MessagingSystemException("Problem setting JMSReplyTo header", e);
            }
        });
        JMS_HEADER_OPERATIONS.put(JMS_TYPE, (message, value) -> {
            checkArgumentType(value, String.class);
            try {
                message.setJMSType((String) value);
            } catch (JMSException | RuntimeException e) {
                throw new MessagingSystemException("Problem setting JMSType header", e);
            }
        });
    }

    private final String key;
    private final Object value;
    private final boolean isJmsHeader;

    /**
     * Creates a container for the message header.
     *
     * @param key   the header name
     * @param value the header value
     */
    public MessageHeader(String key, Object value) {
        this.key = key;
        this.value = value;
        isJmsHeader = JMS_HEADER_OPERATIONS.containsKey(key);
    }

    /**
     * If this is a supported JMS header, attempts to set the header given by
     * the {@code key} with the value given with {@code value}, doing all
     * required type conversions. Not all JMS headers are supported since most
     * are set by the JMS provider (e.g. {@link JMSHeaders#JMS_PRIORITY}).
     *
     * If the header isn't a JMS header, sets an object property. This is only
     * supported for Strings and objectified primitive object types (Boolean,
     * Byte, Short, Integer, Long, Float, Double).
     *
     * @param message the message
     */
    public void apply(Message message) {
        if (isJmsHeader) {
            JMS_HEADER_OPERATIONS.get(key).accept(message, value);
        } else {
            try {
                message.setObjectProperty(encode(key), value);
            } catch (JMSException | RuntimeException e) {
                throw new MessagingClientException(
                    "Problem setting message property '" + key + "' (non-JMS header)", e);
            }
        }
    }

    @Override
    public String toString() {
        return "MessageHeader{" +
            "key='" + key + '\'' +
            ", value=" + value +
            ", isJmsHeader=" + isJmsHeader +
            '}';
    }

    private static void checkArgumentType(Object value, Class<?> clazz) {
        if (value != null && !clazz.isAssignableFrom(value.getClass())) {
            throw new IllegalArgumentException(
                    "Cannot convert " + value + " to " + clazz.getName());
        }
    }
}
