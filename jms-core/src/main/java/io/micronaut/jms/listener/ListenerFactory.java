package io.micronaut.jms.listener;

import io.micronaut.context.BeanContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.inject.Singleton;

import javax.jms.MessageListener;

@Singleton
public class ListenerFactory {
    private final BeanContext context;

    public ListenerFactory(BeanContext context) {
        this.context = context;
    }

    public <T extends Broker> void register(String connectionFactory, T queue, MessageListener listener) {
        final JMSConnectionPool connectionPool = context.getBean(
                JMSConnectionPool.class, Qualifiers.byName(connectionFactory));
        context.registerSingleton(Listener.class, Listener.register(connectionPool, queue, listener), Qualifiers.byName(queue.getName()));
    }
}
