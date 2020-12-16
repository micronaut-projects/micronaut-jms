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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

/***
 *
 * Pool for ensuring maximum reuse of {@link MessageProducer} objects.
 *
 * @see PooledProducer
 * @see MessageProducerPoolFactory
 * @see SessionPool
 *
 * @author elliott
 */
public class MessageProducerPool extends AbstractPool<PooledObject<MessageProducer>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProducerPool.class);

    private final Session session;

    public MessageProducerPool(int initialSize,
                               int maxSize,
                               Session session) {
        super(initialSize, maxSize);
        this.session = session;
    }

    @Override
    protected PooledObject<MessageProducer> create(Object... args) {
        try {
            return new PooledProducer(this, session.createProducer((Destination) args[0]));
        } catch (JMSException e) {
            LOGGER.error("failed to create Producer for pool.", e);
            return null;
        }
    }

    @Override
    protected void reset(PooledObject<MessageProducer> pooledObject) {
        // TODO: implement sensible actions to reset the MessageProducer to avoid rolling over specialized configuration.
    }
}
