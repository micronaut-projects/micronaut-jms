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

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;

/***
 * Pool for ensuring maximum reuse of {@link Session}s within an application.
 *
 * @see SessionPoolFactory
 * @see PooledConnection
 *
 * @author elliott
 */
public class SessionPool extends AbstractPool<PooledObject<Session>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionPool.class);

    private final Connection connection;
    private final MessageProducerPoolFactory producerPoolFactory;

    public SessionPool(
            Integer initialSize,
            Integer maxSize,
            Connection connection,
            MessageProducerPoolFactory producerPoolFactory) {
        super(initialSize, maxSize);
        this.connection = connection;
        this.producerPoolFactory = producerPoolFactory;
    }

    @Override
    protected PooledObject<Session> create(Object... args) {
        try {
            Session session = null;
            if (args == null || args.length == 0) {
                session = connection.createSession();
            } else if (args.length == 1) {
                assert Integer.class.isAssignableFrom(args[0].getClass());
                session = connection.createSession((Integer) args[0]);
            } else if (args.length == 2) {
                assert Boolean.class.isAssignableFrom(args[0].getClass());
                assert Integer.class.isAssignableFrom(args[1].getClass());
                session = connection.createSession((Boolean) args[0], (Integer) args[1]);
            }
            if (session != null) {
                return new PooledSession(
                        this,
                        session,
                        producerPoolFactory.getProducerPool(session));
            }
            throw new IllegalArgumentException("Cannot create a Session from provided arguments.");
        } catch (JMSException e) {
            LOGGER.error("Failed to create session for the pool.", e);
            return null;
        }
    }

    @Override
    protected void reset(PooledObject<Session> pooledObject) {
        // TODO:  implement sensible actions to reset a javax.jms.Session so it can be reused.
    }
}
