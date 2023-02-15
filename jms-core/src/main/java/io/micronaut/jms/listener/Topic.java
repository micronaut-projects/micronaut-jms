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

public class Topic extends Broker implements javax.jms.Topic {
    private final boolean shared;

    private final boolean durable;

    public Topic(String destination, int acknowledgeMode, boolean transacted, String messageSelector, Class<?> targetClass, boolean shared, boolean durable) {
        super(destination, acknowledgeMode, transacted, messageSelector, targetClass);
        this.shared = shared;
        this.durable = durable;
    }

    public static Topic fromAnnotation(
            ExecutableMethod<?, ?> method,
            AnnotationValue<io.micronaut.jms.annotations.Topic> annotation) {
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
        final boolean shared = annotation.getRequiredValue("shared", Boolean.class);
        final boolean durable = annotation.getRequiredValue("durable", Boolean.class);

        return new Topic(destination, acknowledgeMode, transacted, messageSelector.orElse(null), targetClass, shared, durable);
    }

    @Override
    public String getTopicName() throws JMSException {
        return this.getName();
    }

    public boolean isShared() {
        return shared;
    }

    public boolean isDurable() {
        return durable;
    }

    public Destination getDestination(Session session) throws JMSException {
        return session.createTopic(this.name);
    }
}
