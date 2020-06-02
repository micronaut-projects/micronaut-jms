package io.micronaut.jms.listener;

import io.micronaut.jms.serdes.DefaultSerializerDeserializer;

import javax.jms.BytesMessage;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;
import java.io.Serializable;
import java.util.Map;

public class MessageHandlerAdapter<T> implements MessageListener {

    private MessageHandler<T> delegate;
    private Class<T> clazz;
    private final DefaultSerializerDeserializer serdes = new DefaultSerializerDeserializer();

    public MessageHandlerAdapter(MessageHandler<T> delegate, Class<T> clazz) {
        this.delegate = delegate;
        this.clazz = clazz;
    }

    @Override
    public void onMessage(Message message) {
        if (messageTypeMatchesHandler(message)) {
            delegate.handle((T) serdes.deserialize(message));
        }
    }

    private boolean messageTypeMatchesHandler(Message message) {
        if (message instanceof TextMessage && clazz.isAssignableFrom(String.class)) {
            return true;
        }
        if (message instanceof MapMessage && clazz.isAssignableFrom(Map.class)) {
            return true;
        }
        if (message instanceof BytesMessage && clazz.isAssignableFrom(byte[].class)) {
            return true;
        }
        if (message instanceof ObjectMessage && clazz.isAssignableFrom(Serializable.class)) {
            return true;
        }
        return false;
    }
}
