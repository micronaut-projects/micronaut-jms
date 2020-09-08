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
