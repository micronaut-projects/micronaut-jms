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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static javax.jms.Message.DEFAULT_PRIORITY;

/***
 * Utility class, allowing for access to the supported JMS Headers, and methods for extracting those values from
 *      a given {@link Message}.
 *
 * @author elliottpope
 * @since 1.0
 */
public final class JMSHeaders {

    private static final int MIN_PRIORITY = 0;
    private static final int MAX_PRIORITY = 9;

    /***
     * Name of the JMS Correlation ID header. Specifies an ID so that the {@link Message} can be linked to other
     *      messages.
     *
     * @see Message#getJMSCorrelationID()
     */
    public static final String JMS_CORRELATION_ID = "JMSCorrelationID";

    public static final String JMS_DELIVERY_MODE = "JMSDeliveryMode";

    public static final String JMS_DELIVERY_TIME = "JMSDeliveryTime";

    /***
     * Name of the JMS Destination header. Specifying the destination of a {@link Message} or where it was received from.
     *
     * @see Message#getJMSDestination()
     */
    public static final String JMS_DESTINATION = "JMSDestination";

    public static final String JMS_EXPIRATION = "JMSExpiration";

    /***
     * Name of the JMS ID header. Specifies a unique ID for a {@link Message}.
     *
     * @see Message#getJMSMessageID()
     */
    public static final String JMS_MESSAGE_ID = "JMSMessageID";

    public static final String JMS_PRIORITY = "JMSPriority";

    public static final String JMS_REDELIVERED = "JMSRedelivered";

    public static final String JMS_REPLY_TO = "JMSReplyTo";

    public static final String JMS_TIMESTAMP = "JMSTimestamp";

    /***
     * Name of the JMS Type header.
     *
     * @see Message#getJMSType()
     */
    public static final String JMS_TYPE = "JMSType";

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSHeaders.class);

    private static final Set<String> ALL_HEADER_NAMES = new HashSet<>(Arrays.asList(
        JMS_CORRELATION_ID, JMS_DELIVERY_MODE, JMS_DELIVERY_TIME,
        JMS_DESTINATION, JMS_EXPIRATION, JMS_MESSAGE_ID, JMS_PRIORITY,
        JMS_REDELIVERED, JMS_REPLY_TO, JMS_TIMESTAMP, JMS_TYPE
    ));

    private JMSHeaders() {
    }

    /***
     * @param headerName - the name of the header to test.
     * @return true if the given {@param headerName} is a supported JMS Header name, false otherwise.
     */
    public static boolean isJMSHeader(String headerName) {
        return ALL_HEADER_NAMES.contains(headerName);
    }

    /***
     *
     * Attempts to retrieve the value of the header given by {@param headerName} from the given {@param message}.
     *      If no value is present, or there is some error extracting the header, then the result will be null.
     *
     * @param headerName - the name of the header to be extracted.
     * @param message - the {@link Message} to extract the header from.
     * @param clazz - the expected class of the header value.
     * @param <T> - the expected class of the header value.
     * @return the value of the header on the given {@param message} specified by {@param headerName} as an object
     *      of type {@param clazz}.
     */
    public static @Nullable <T> T getHeader(String headerName,
                                            Message message,
                                            Class<T> clazz) {
        try {
            if (isJMSHeader(headerName)) {
                return getJMSHeader(headerName, message, clazz);
            }
            return getClientProvidedHeader(headerName, message, clazz);
        } catch (JMSException e) {
            LOGGER.error("Failed to extract the header: " + headerName, e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getJMSHeader(String headerName,
                                      Message message,
                                      Class<T> clazz) throws JMSException {
        switch (headerName) {
            case JMS_CORRELATION_ID:
                return getCorrelationIdHeader(message, clazz);
            case JMS_DELIVERY_MODE:
                return getDeliveryModeHeader(message, clazz);
            case JMS_DELIVERY_TIME:
                return getDeliveryTimeHeader(message, clazz);
            case JMS_DESTINATION:
                return getDestinationHeader(message, clazz);
            case JMS_EXPIRATION:
                return getExpirationHeader(message, clazz);
            case JMS_MESSAGE_ID:
                return getMessageIdHeader(message, clazz);
            case JMS_PRIORITY:
                return getPriorityHeader(message, clazz);
            case JMS_REDELIVERED:
                return getRedeliveredHeader(message, clazz);
            case JMS_REPLY_TO:
                return getReplyToHeader(message, clazz);
            case JMS_TIMESTAMP:
                return getTimestampHeader(message, clazz);
            case JMS_TYPE:
                return getTypeHeader(message, clazz);
            default:
                throw new IllegalArgumentException("No action defined for JMSHeader " + headerName);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T getCorrelationIdHeader(Message message, Class<T> clazz) throws JMSException {
        checkArgumentType(JMS_CORRELATION_ID, clazz, byte[].class, String.class);
        return (T) (String.class.isAssignableFrom(clazz) ? message.getJMSCorrelationID()
            : message.getJMSCorrelationIDAsBytes());
    }

    private static <T> T getDeliveryModeHeader(Message message, Class<T> clazz) throws JMSException {
        checkArgumentType(JMS_DELIVERY_MODE, clazz, int.class, Integer.class, String.class);
        return (T) (String.class.isAssignableFrom(clazz)
            ? JMSDeliveryMode.from(message.getJMSDeliveryMode()).name()
            : message.getJMSDeliveryMode());
    }

    private static <T> T getDeliveryTimeHeader(Message message, Class<T> clazz) throws JMSException {
        checkArgumentType(JMS_DELIVERY_TIME, clazz, long.class, Long.class, Date.class, String.class);
        return convertDateHeaderValue(message.getJMSDeliveryTime(), clazz);
    }

    private static <T> T getDestinationHeader(Message message, Class<T> clazz) throws JMSException {
        checkArgumentType(JMS_DESTINATION, clazz, Destination.class, String.class);
        return convertDestinationHeaderValue(message.getJMSDestination(), clazz);
    }

    private static <T> T getExpirationHeader(Message message, Class<T> clazz) throws JMSException {
        checkArgumentType(JMS_EXPIRATION, clazz, long.class, Long.class, Date.class, String.class);
        return convertDateHeaderValue(message.getJMSExpiration(), clazz);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getMessageIdHeader(Message message, Class<T> clazz) throws JMSException {
        checkArgumentType(JMS_MESSAGE_ID, clazz, String.class);
        return (T) message.getJMSMessageID();
    }

    private static <T> T getPriorityHeader(Message message, Class<T> clazz) throws JMSException {
        checkArgumentType(JMS_PRIORITY, clazz, int.class, Integer.class, String.class);
        int priority = message.getJMSPriority();
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            priority = DEFAULT_PRIORITY;
        }
        return (T) (String.class.isAssignableFrom(clazz) ? String.valueOf(priority) : priority);
    }

    private static <T> T getRedeliveredHeader(Message message, Class<T> clazz) throws JMSException {
        checkArgumentType(JMS_REDELIVERED, clazz, boolean.class, Boolean.class, String.class);
        return (T) (String.class.isAssignableFrom(clazz)
            ? String.valueOf(message.getJMSRedelivered())
            : Boolean.valueOf(message.getJMSRedelivered()));
    }

    private static <T> T getReplyToHeader(Message message, Class<T> clazz) throws JMSException {
        checkArgumentType(JMS_REPLY_TO, clazz, Destination.class, String.class);
        return convertDestinationHeaderValue(message.getJMSReplyTo(), clazz);
    }

    private static <T> T getTimestampHeader(Message message, Class<T> clazz) throws JMSException {
        checkArgumentType(JMS_TIMESTAMP, clazz, long.class, Long.class, Date.class, String.class);
        return convertDateHeaderValue(message.getJMSTimestamp(), clazz);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getTypeHeader(Message message, Class<T> clazz) throws JMSException {
        checkArgumentType(JMS_TYPE, clazz, String.class);
        return (T) message.getJMSType();
    }

    private static void checkArgumentType(String headerName,
                                          Class<?> givenClass,
                                          Class<?>... targetClasses) {
        Stream.of(targetClasses)
            .filter(clazz -> clazz.isAssignableFrom(givenClass))
            .findAny()
            .orElseThrow(() -> new IllegalArgumentException(
                "Cannot convert " + headerName + " to " + givenClass.getName()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T convertDateHeaderValue(long value, Class<T> clazz) {
        if (Date.class.isAssignableFrom(clazz)) {
            return (T) new Date(value);
        }
        if (String.class.isAssignableFrom(clazz)) {
            return (T) new Date(value).toString();
        }
        return (T) Long.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T convertDestinationHeaderValue(Destination value, Class<T> clazz) {
        if (value == null) {
            return null;
        }
        return (T) (Destination.class.isAssignableFrom(clazz) ? value : value.toString());
    }

    private static <T> T getClientProvidedHeader(String headerName,
                                                 Message message,
                                                 Class<T> clazz) throws JMSException {

        if (!message.propertyExists(headerName)) {
            return null;
        }

        if (boolean.class.isAssignableFrom(clazz) || Boolean.class.isAssignableFrom(clazz) ) {
            return (T) Boolean.valueOf(message.getBooleanProperty(headerName));
        }

        if (byte.class.isAssignableFrom(clazz) || Byte.class.isAssignableFrom(clazz) ) {
            return (T) Byte.valueOf(message.getByteProperty(headerName));
        }

        if (double.class.isAssignableFrom(clazz) || Double.class.isAssignableFrom(clazz) ) {
            return (T) Double.valueOf(message.getDoubleProperty(headerName));
        }

        if (float.class.isAssignableFrom(clazz) || Float.class.isAssignableFrom(clazz) ) {
            return (T) Float.valueOf(message.getFloatProperty(headerName));
        }

        if (int.class.isAssignableFrom(clazz) || Integer.class.isAssignableFrom(clazz) ) {
            return (T) Integer.valueOf(message.getIntProperty(headerName));
        }

        if (long.class.isAssignableFrom(clazz) || Long.class.isAssignableFrom(clazz)) {
            return (T) Long.valueOf(message.getLongProperty(headerName));
        }

        if (short.class.isAssignableFrom(clazz) || Short.class.isAssignableFrom(clazz) ) {
            return (T) Short.valueOf(message.getShortProperty(headerName));
        }

        if (String.class.isAssignableFrom(clazz)) {
            return (T) message.getStringProperty(headerName);
        }

        return (T) message.getObjectProperty(headerName);
    }
}
