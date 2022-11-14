package io.micronaut.jms.listener;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.messaging.annotation.MessageBody;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;
import java.util.Optional;
import java.util.stream.Stream;

public class Queue extends Broker implements javax.jms.Queue {
    private Queue(String destination, int acknowledgeMode, boolean transacted, String messageSelector, Class<?> targetClass) {
        super(destination, acknowledgeMode, transacted, messageSelector, targetClass);
    }

    public static Queue fromAnnotation(
            ExecutableMethod<?, ?> method,
            AnnotationValue<io.micronaut.jms.annotations.Queue> annotation) {
        final Class<?> targetClass = Stream.of(method.getArguments())
                .filter(arg ->
                        arg.isDeclaredAnnotationPresent(MessageBody.class) ||
                                arg.isDeclaredAnnotationPresent(io.micronaut.jms.annotations.Message.class))
                .findAny()
                .map(Argument::getClass)
                .get();

        final String destination = annotation.getRequiredValue(String.class);
        final int acknowledgeMode = annotation.getRequiredValue("acknowledgeMode", Integer.class);
        final boolean transacted = annotation.getRequiredValue("transacted", Boolean.class);
        final Optional<String> messageSelector = annotation.get("messageSelector", String.class);

        return new Queue(destination, acknowledgeMode, transacted, messageSelector.orElse(null), targetClass);
    }

    @Override
    public String getQueueName() throws JMSException {
        return this.getName();
    }

    public Destination getDestination(Session session) throws JMSException {
        return session.createQueue(this.name);
    }
}
