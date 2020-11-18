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
import java.util.Date;
import java.util.Enumeration;
import java.util.stream.Stream;

/***
 * Utility class, allowing for access to the supported JMS Headers, and methods for extracting those values from
 *      a given {@link Message}.
 *
 * @author elliottpope
 * @since 1.0
 */
public final class JMSHeaders {
    /***
     * Name of the JMS Destination header. Specifying the destination of a {@link Message} or where it was received from.
     *
     * @see Message#getJMSDestination()
     */
    public static final String JMS_DESTINATION = "JMSDestination";

    /***
     * Name of the JMS ID header. Specifies a unique ID for a {@link Message}.
     *
     * @see Message#getJMSMessageID()
     */
    public static final String JMS_MESSAGE_ID = "JMSMessageID";

    /***
     * Name of the JMS Correlation ID header. Specifies an ID so that the {@link Message} can be linked to other
     *      messages.
     *
     * @see Message#getJMSCorrelationID()
     */
    public static final String JMS_CORRELATION_ID = "JMSCorrelationID";

    /***
     * Name of the JMS Type header.
     *
     * @see Message#getJMSType()
     */
    public static final String JMS_TYPE = "JMSType";
    public static final String JMS_DELIVERY_MODE = "JMSDeliveryMode";
    public static final String JMS_EXPIRATION = "JMSExpiration";
    public static final String JMS_REDELIVERED = "JMSRedelivered";
    public static final String JMS_PRIORITY = "JMSPriority";
    public static final String JMS_REPLY_TO = "JMSReplyTo";

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSHeaders.class);

    private static final String[] VALUES = new String[] {
            JMS_DESTINATION, JMS_MESSAGE_ID, JMS_CORRELATION_ID, JMS_TYPE, JMS_DELIVERY_MODE,
            JMS_EXPIRATION, JMS_REDELIVERED, JMS_PRIORITY, JMS_REPLY_TO
    };

    private JMSHeaders() {

    }

    /***
     * @param headerName - the name of the header to test.
     * @return true if the given {@param headerName} is a supported JMS Header name, false otherwise.
     */
    public static boolean isJMSHeader(String headerName) {
        for (String header : VALUES) {
            if (header.equals(headerName)) {
                return true;
            }
        }
        return false;
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
    public static @Nullable <T> T getHeader(String headerName, Message message, Class<T> clazz) {
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

    private static void checkArgumentType(String property, Class<?> givenClass, Class<?>... targetClasses) {
        Stream.of(targetClasses)
                .filter(clazz -> clazz.isAssignableFrom(givenClass))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Cannot convert " + property + " to " + givenClass.getName()));
    }

    private static <T> T getJMSHeader(String header, Message message, Class<T> clazz) throws JMSException {
        switch (header) {
            case JMS_TYPE:
                checkArgumentType("JMSType", clazz, String.class);
                return (T) message.getJMSType();
            case JMS_PRIORITY:
                checkArgumentType("JMSPriority", clazz, Integer.class, String.class);
                Integer priority = message.getJMSPriority();
                if (priority < 1 || priority > 9) {
                    priority = Message.DEFAULT_PRIORITY;
                }
                return (T) (Integer.class.isAssignableFrom(clazz) ? priority :
                        String.valueOf(priority));
            case JMS_REPLY_TO:
                checkArgumentType("JMSReplyTo", clazz, Destination.class, String.class);
                return (T) (Destination.class.isAssignableFrom(clazz) ? message.getJMSReplyTo()
                        : message.getJMSReplyTo().toString());
            case JMS_EXPIRATION:
                checkArgumentType("JMSExpiration", clazz, Long.class, String.class, Date.class);
                if (Date.class.isAssignableFrom(clazz)) {
                    return (T) new Date(message.getJMSExpiration());
                }
                if (String.class.isAssignableFrom(clazz)) {
                    return (T) new Date(message.getJMSExpiration()).toString();
                }
                return (T) Long.valueOf(message.getJMSExpiration());
            case JMS_MESSAGE_ID:
                checkArgumentType("JMSMessageID", clazz, String.class);
                return (T) message.getJMSMessageID();
            case JMS_DESTINATION:
                checkArgumentType("JMSDestination", clazz, Destination.class, String.class);
                return (T) (Destination.class.isAssignableFrom(clazz) ? message.getJMSDestination()
                        : message.getJMSDestination().toString());
            case JMS_REDELIVERED:
                checkArgumentType("JMSRedelivered", clazz, Boolean.class, String.class);
                return (T) (Boolean.class.isAssignableFrom(clazz) ? Boolean.valueOf(message.getJMSRedelivered())
                        : String.valueOf(message.getJMSRedelivered()));
            case JMS_DELIVERY_MODE:
                checkArgumentType("JMSDeliveryMode", clazz, Integer.class, String.class);
                return (T) (Integer.class.isAssignableFrom(clazz) ? Integer.valueOf(message.getJMSDeliveryMode())
                        : JMSDeliveryMode.from(message.getJMSDeliveryMode()).toString());
            case JMS_CORRELATION_ID:
                checkArgumentType("JMSCorrelationID", clazz, String.class);
                return (T) message.getJMSCorrelationID();
            default:
                throw new IllegalArgumentException("No action defained for JMSHeader " + header);
        }
    }

    private static <T> T getClientProvidedHeader(String headerName, Message message, Class<T> clazz) throws JMSException {
        Enumeration<String> properties = message.getPropertyNames();
        while (properties.hasMoreElements()) {
            if (headerName.equals(properties.nextElement())) {
                return getHeaderValue(headerName, message, clazz);
            }
        }
        return null;
    }

    private static <T> T getHeaderValue(String name, Message message, Class<T> clazz) throws JMSException {

        if (Boolean.class.isAssignableFrom(clazz)) {
            return (T) Boolean.valueOf(message.getBooleanProperty(name));
        }
        if (String.class.isAssignableFrom(clazz)) {
            return (T) message.getStringProperty(name);
        }
        if (Long.class.isAssignableFrom(clazz)) {
            return (T) Long.valueOf(message.getLongProperty(name));
        }
        if (Integer.class.isAssignableFrom(clazz)) {
            return (T) Integer.valueOf(message.getIntProperty(name));
        }
        return (T) message.getObjectProperty(name);
    }
}
