package io.micronaut.jms.listener;

import jakarta.annotation.Nullable;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

public abstract class Broker {
    protected final String name;
    protected final int acknowledgeMode;
    protected final boolean transacted;
    protected final @Nullable String messageSelector;
    protected final Class<?> targetClass;

    public Broker(String destination, int acknowledgeMode, boolean transacted, String messageSelector, Class<?> targetClass) {
        this.name = destination;
        this.acknowledgeMode = acknowledgeMode;
        this.transacted = transacted;
        this.messageSelector = messageSelector;
        this.targetClass = targetClass;
    }

    public String getName() {
        return name;
    }

    public abstract Destination getDestination(Session session) throws JMSException;

    public int getAcknowledgeMode() {
        return acknowledgeMode;
    }

    public boolean isTransacted() {
        return transacted;
    }

    public String getMessageSelector() {
        return messageSelector;
    }

    public Class<?> getTargetClass() {
        return targetClass;
    }
}
