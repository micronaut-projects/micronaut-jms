package io.micronaut.jms.model;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import java.util.Date;
import java.util.stream.Stream;

public final class JMSHeaders {
    public static final String JMS_DESTINATION = "JMSDestination";
    public static final String JMS_MESSAGE_ID = "JMSMessageID";
    public static final String JMS_CORRELATION_ID = "JMSCorrelationID";
    public static final String JMS_TYPE = "JMSType";
    public static final String JMS_DELIVERY_MODE = "JMSDeliveryMode";
    public static final String JMS_EXPIRATION = "JMSExpiration";
    public static final String JMS_REDELIVERED = "JMSRedelivered";
    public static final String JMS_PRIORITY = "JMSPriority";
    public static final String JMS_REPLY_TO = "JMSReplyTo";

    private static final String[] VALUES = new String[] {
            JMS_DESTINATION, JMS_MESSAGE_ID, JMS_CORRELATION_ID, JMS_TYPE, JMS_DELIVERY_MODE,
            JMS_EXPIRATION, JMS_REDELIVERED, JMS_PRIORITY, JMS_REPLY_TO
    };

    public static boolean isJMSHeader(String headerName) {
        for (String header : VALUES) {
            if (header.equals(headerName)) {
                return true;
            }
        }
        return false;
    }

    public static <T> T getHeader(String headerName, Message message, Class<T> clazz) {
        try {
            if (isJMSHeader(headerName)) {
                return getJMSHeader(headerName, message, clazz);
            }
            return getClientProvidedHeader(headerName, message, clazz);
        } catch (JMSException e) {
            e.printStackTrace();
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
        if (Boolean.class.isAssignableFrom(clazz)) {
            return (T) Boolean.valueOf(message.getBooleanProperty(headerName));
        }
        if (String.class.isAssignableFrom(clazz)) {
            return (T) message.getStringProperty(headerName);
        }
        if (Long.class.isAssignableFrom(clazz)) {
            return (T) Long.valueOf(message.getLongProperty(headerName));
        }
        if (Integer.class.isAssignableFrom(clazz)) {
            return (T) Integer.valueOf(message.getIntProperty(headerName));
        }
        return (T) message.getObjectProperty(headerName);
    }
}
