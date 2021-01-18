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
package io.micronaut.jms.pool;

import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

/**
 * Pooled object for ensuring maximum reuse of {@link MessageProducer} objects.
 *
 * @author Elliott Pope
 * @see MessageProducerPool
 * @see MessageProducerPoolFactory
 * @see SessionPool
 * @since 1.0.0
 */
public class PooledProducer extends PooledObject<MessageProducer> implements MessageProducer {

    public PooledProducer(AbstractPool<PooledObject<MessageProducer>> pool,
                          MessageProducer object) {
        super(pool, object);
    }

    public static PooledProducer of(PooledObject<MessageProducer> object) {
        return (PooledProducer) object;
    }

    @Override
    public void setDisableMessageID(boolean value) throws JMSException {
        get().setDisableMessageID(value);
    }

    @Override
    public boolean getDisableMessageID() throws JMSException {
        return get().getDisableMessageID();
    }

    @Override
    public void setDisableMessageTimestamp(boolean value) throws JMSException {
        get().setDisableMessageTimestamp(value);
    }

    @Override
    public boolean getDisableMessageTimestamp() throws JMSException {
        return get().getDisableMessageTimestamp();
    }

    @Override
    public void setDeliveryMode(int deliveryMode) throws JMSException {
        get().setDeliveryMode(deliveryMode);
    }

    @Override
    public int getDeliveryMode() throws JMSException {
        return get().getDeliveryMode();
    }

    @Override
    public void setPriority(int defaultPriority) throws JMSException {
        get().setPriority(defaultPriority);
    }

    @Override
    public int getPriority() throws JMSException {
        return get().getPriority();
    }

    @Override
    public void setTimeToLive(long timeToLive) throws JMSException {
        get().setTimeToLive(timeToLive);
    }

    @Override
    public long getTimeToLive() throws JMSException {
        return get().getTimeToLive();
    }

    @Override
    public void setDeliveryDelay(long deliveryDelay) throws JMSException {
        get().setDeliveryDelay(deliveryDelay);
    }

    @Override
    public long getDeliveryDelay() throws JMSException {
        return get().getDeliveryDelay();
    }

    @Override
    public Destination getDestination() throws JMSException {
        return get().getDestination();
    }

    @Override
    public void send(Message message) throws JMSException {
        get().send(message);
    }

    @Override
    public void send(Message message,
                     int deliveryMode,
                     int priority,
                     long timeToLive) throws JMSException {
        get().send(message, deliveryMode, priority, timeToLive);
    }

    @Override
    public void send(Destination destination,
                     Message message) throws JMSException {
        get().send(destination, message);
    }

    @Override
    public void send(Destination destination,
                     Message message,
                     int deliveryMode,
                     int priority,
                     long timeToLive) throws JMSException {
        get().send(destination, message, deliveryMode, priority, timeToLive);
    }

    @Override
    public void send(Message message,
                     CompletionListener completionListener) throws JMSException {
        get().send(message, completionListener);
    }

    @Override
    public void send(Message message,
                     int deliveryMode,
                     int priority,
                     long timeToLive,
                     CompletionListener completionListener) throws JMSException {
        get().send(message, deliveryMode, priority, timeToLive, completionListener);
    }

    @Override
    public void send(Destination destination,
                     Message message,
                     CompletionListener completionListener) throws JMSException {
        get().send(destination, message, completionListener);
    }

    @Override
    public void send(Destination destination,
                     Message message,
                     int deliveryMode,
                     int priority,
                     long timeToLive,
                     CompletionListener completionListener) throws JMSException {
        get().send(destination, message, deliveryMode, priority, timeToLive, completionListener);
    }

    @Override
    public String toString() {
        return "PooledProducer{MessageProducer=" + get() + '}';
    }
}
