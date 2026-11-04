package io.micronaut.jms.tracing.opentelemetry;

import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDK proxy stubs of JMS messages, for the tracing tests.
 */
final class MessageStubs {

    private MessageStubs() {
    }

    /**
     * @param type   The interface to stub
     * @param answer The answer to the methods other than {@code equals}, {@code hashCode} and {@code toString}
     * @param <T>    The type
     * @return The stub
     */
    static <T> T stub(Class<T> type, Answer answer) {
        Object stub = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> type.getSimpleName() + " stub";
            default -> answer.answer(method.getName(), args == null ? new Object[0] : args);
        });
        return type.cast(stub);
    }

    /**
     * A text message whose string properties are kept in a map.
     *
     * @param text      The text
     * @param messageId The message ID, or {@code null}
     * @return The message
     */
    static TextMessage message(String text, @Nullable String messageId) {
        Map<String, String> properties = new ConcurrentHashMap<>();
        return stub(TextMessage.class, (name, args) -> switch (name) {
            case "getText" -> text;
            case "getJMSMessageID" -> messageId;
            case "setStringProperty" -> properties.put((String) args[0], (String) args[1]);
            case "getStringProperty" -> properties.get((String) args[0]);
            case "getPropertyNames" -> Collections.enumeration(properties.keySet());
            default -> null;
        });
    }

    /**
     * Answers a stubbed method.
     */
    @FunctionalInterface
    interface Answer {
        /**
         * @param method The method name
         * @param args   The arguments
         * @return The result
         * @throws JMSException If the method fails
         */
        @Nullable Object answer(String method, Object[] args) throws JMSException;
    }
}
