package io.micronaut.jms.listener;

@FunctionalInterface
public interface MessageHandler<T> {
    void handle(T message);
}
