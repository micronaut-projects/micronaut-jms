package io.micronaut.jms.pool;

import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

/***
 *
 * Pooled object for ensuring maximum reuse of {@link MessageProducer} objects.
 *
 * @see MessageProducerPool
 * @see MessageProducerPoolFactory
 * @see SessionPool
 *
 * @author elliott
 */
public class PooledProducer extends PooledObject<MessageProducer> implements MessageProducer {

    public PooledProducer(AbstractPool<PooledObject<MessageProducer>> pool, MessageProducer object) {
        super(pool, object);
    }

    @Override
    public void setDisableMessageID(boolean value) throws JMSException {
        object.setDisableMessageID(value);
    }

    @Override
    public boolean getDisableMessageID() throws JMSException {
        return object.getDisableMessageID();
    }

    @Override
    public void setDisableMessageTimestamp(boolean value) throws JMSException {
        object.setDisableMessageTimestamp(value);
    }

    @Override
    public boolean getDisableMessageTimestamp() throws JMSException {
        return object.getDisableMessageTimestamp();
    }

    @Override
    public void setDeliveryMode(int deliveryMode) throws JMSException {
        object.setDeliveryMode(deliveryMode);
    }

    @Override
    public int getDeliveryMode() throws JMSException {
        return object.getDeliveryMode();
    }

    @Override
    public void setPriority(int defaultPriority) throws JMSException {
        object.setPriority(defaultPriority);
    }

    @Override
    public int getPriority() throws JMSException {
        return object.getPriority();
    }

    @Override
    public void setTimeToLive(long timeToLive) throws JMSException {
        object.setTimeToLive(timeToLive);
    }

    @Override
    public long getTimeToLive() throws JMSException {
        return object.getTimeToLive();
    }

    @Override
    public void setDeliveryDelay(long deliveryDelay) throws JMSException {
        object.setDeliveryDelay(deliveryDelay);
    }

    @Override
    public long getDeliveryDelay() throws JMSException {
        return object.getDeliveryDelay();
    }

    @Override
    public Destination getDestination() throws JMSException {
        return object.getDestination();
    }

    @Override
    public void send(Message message) throws JMSException {
        object.send(message);
    }

    @Override
    public void send(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        object.send(message, deliveryMode, priority, timeToLive);
    }

    @Override
    public void send(Destination destination, Message message) throws JMSException {
        object.send(destination, message);
    }

    @Override
    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        object.send(destination, message, deliveryMode, priority, timeToLive);
    }

    @Override
    public void send(Message message, CompletionListener completionListener) throws JMSException {
        object.send(message, completionListener);
    }

    @Override
    public void send(Message message, int deliveryMode, int priority, long timeToLive, CompletionListener completionListener) throws JMSException {
        object.send(message, deliveryMode, priority, timeToLive, completionListener);
    }

    @Override
    public void send(Destination destination, Message message, CompletionListener completionListener) throws JMSException {
        object.send(destination, message, completionListener);
    }

    @Override
    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive, CompletionListener completionListener) throws JMSException {
        object.send(destination, message, deliveryMode, priority, timeToLive, completionListener);
    }

    public static PooledProducer of(PooledObject<MessageProducer> object) {
        return (PooledProducer) object;
    }
}
