package io.micronaut.jms.observation;

import io.micronaut.jms.model.JMSDestinationType;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JDK proxy stubs of JMS objects and an observer that records what it observes, for the observation tests.
 */
public final class ObservationStubs {

    private ObservationStubs() {
    }

    /**
     * @param type   The interface to stub
     * @param answer The answer to the methods other than {@code equals}, {@code hashCode} and {@code toString}
     * @param <T>    The type
     * @return The stub
     */
    public static <T> T stub(Class<T> type, Answer answer) {
        Object stub = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object result = switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> type.getSimpleName() + " stub";
                default -> answer.answer(method.getName(), args == null ? new Object[0] : args);
            };
            if (result == null && method.getReturnType() == boolean.class) {
                return false;
            }
            if (result == null && method.getReturnType() == long.class) {
                return 0L;
            }
            if (result == null && method.getReturnType() == int.class) {
                return 0;
            }
            return result;
        });
        return type.cast(stub);
    }

    /**
     * A text message whose string properties are kept in a map.
     *
     * @param text        The text
     * @param destination The destination, or {@code null}
     * @param messageId   The message ID, or {@code null}
     * @return The message
     */
    public static TextMessage message(String text, @Nullable Destination destination, @Nullable String messageId) {
        Map<String, String> properties = new ConcurrentHashMap<>();
        return stub(TextMessage.class, (name, args) -> switch (name) {
            case "getText" -> text;
            case "getJMSDestination" -> destination;
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
    public interface Answer {
        /**
         * @param method The method name
         * @param args   The arguments
         * @return The result
         * @throws JMSException If the method fails
         */
        @Nullable Object answer(String method, Object[] args) throws JMSException;
    }

    /**
     * Records the start and the completion of each observation.
     */
    public static class RecordingObserver implements JMSMessageObserver {

        public final List<String> events = new CopyOnWriteArrayList<>();
        private final String name;
        private final int order;

        public RecordingObserver(String name, int order) {
            this.name = name;
            this.order = order;
        }

        @Override
        public JMSObservation process(JMSDestinationType destinationType, String destination, Message message) {
            return observe("process", destinationType, destination);
        }

        @Override
        public JMSObservation send(JMSDestinationType destinationType, String destination, Message message) {
            return observe("send", destinationType, destination);
        }

        @Override
        public int getOrder() {
            return order;
        }

        private JMSObservation observe(String operation, JMSDestinationType destinationType, String destination) {
            String observation = name + " " + operation + " " + destinationType + " " + destination;
            events.add(observation);
            return error -> events.add(observation + " completed" + (error == null ? "" : " with " + error.getClass().getSimpleName()));
        }
    }
}
