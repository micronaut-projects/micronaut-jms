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

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import java.io.Serializable;

public class PooledSession extends PooledObject<Session> implements Session {

    private final MessageProducerPool producerPool;

    public PooledSession(AbstractPool<PooledObject<Session>> pool,
                         Session object,
                         MessageProducerPool producerPool) {
        super(pool, object);
        this.producerPool = producerPool;
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException {
        return get().createBytesMessage();
    }

    @Override
    public MapMessage createMapMessage() throws JMSException {
        return get().createMapMessage();
    }

    @Override
    public Message createMessage() throws JMSException {
        return get().createMessage();
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException {
        return get().createObjectMessage();
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable messageObject) throws JMSException {
        return get().createObjectMessage(messageObject);
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException {
        return get().createStreamMessage();
    }

    @Override
    public TextMessage createTextMessage() throws JMSException {
        return get().createTextMessage();
    }

    @Override
    public TextMessage createTextMessage(String text) throws JMSException {
        return get().createTextMessage(text);
    }

    @Override
    public boolean getTransacted() throws JMSException {
        return get().getTransacted();
    }

    @Override
    public int getAcknowledgeMode() throws JMSException {
        return get().getAcknowledgeMode();
    }

    @Override
    public void commit() throws JMSException {
        get().commit();
    }

    @Override
    public void rollback() throws JMSException {
        get().rollback();
    }

    @Override
    public void recover() throws JMSException {
        get().recover();
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        return get().getMessageListener();
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException {
        get().setMessageListener(listener);
    }

    @Override
    public void run() {
        get().run();
    }

    @Override
    public MessageProducer createProducer(Destination destination) throws JMSException {
        return PooledProducer.of(producerPool.request(destination));
    }

    @Override
    public MessageConsumer createConsumer(Destination destination) throws JMSException {
        return get().createConsumer(destination);
    }

    @Override
    public MessageConsumer createConsumer(Destination destination,
                                          String messageSelector) throws JMSException {
        return get().createConsumer(destination, messageSelector);
    }

    @Override
    public MessageConsumer createConsumer(Destination destination,
                                          String messageSelector,
                                          boolean noLocal) throws JMSException {
        return get().createConsumer(destination, messageSelector, noLocal);
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic,
                                                String sharedSubscriptionName) throws JMSException {
        return get().createSharedConsumer(topic, sharedSubscriptionName);
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic,
                                                String sharedSubscriptionName,
                                                String messageSelector) throws JMSException {
        return get().createSharedConsumer(topic, sharedSubscriptionName, messageSelector);
    }

    @Override
    public Queue createQueue(String queueName) throws JMSException {
        return get().createQueue(queueName);
    }

    @Override
    public Topic createTopic(String topicName) throws JMSException {
        return get().createTopic(topicName);
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic,
                                                   String name) throws JMSException {
        return get().createDurableSubscriber(topic, name);
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic,
                                                   String name,
                                                   String messageSelector,
                                                   boolean noLocal) throws JMSException {
        return get().createDurableSubscriber(topic, name, messageSelector, noLocal);
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic,
                                                 String name) throws JMSException {
        return get().createDurableSubscriber(topic, name);
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic,
                                                 String name,
                                                 String messageSelector,
                                                 boolean noLocal) throws JMSException {
        return get().createDurableSubscriber(topic, name, messageSelector, noLocal);
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic,
                                                       String name) throws JMSException {
        return get().createDurableSubscriber(topic, name);
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic,
                                                       String name,
                                                       String messageSelector) throws JMSException {
        return get().createSharedDurableConsumer(topic, name, messageSelector);
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException {
        return get().createBrowser(queue);
    }

    @Override
    public QueueBrowser createBrowser(Queue queue,
                                      String messageSelector) throws JMSException {
        return get().createBrowser(queue, messageSelector);
    }

    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException {
        return get().createTemporaryQueue();
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException {
        return get().createTemporaryTopic();
    }

    @Override
    public void unsubscribe(String name) throws JMSException {
        get().unsubscribe(name);
    }
}
