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

import io.micronaut.messaging.exceptions.MessagingSystemException;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;
import java.util.Arrays;

/***
 * Pool for ensuring maximum reuse of {@link Session}s within an application.
 *
 * @see SessionPoolFactory
 * @see PooledConnection
 *
 * @author elliott
 */
public class SessionPool extends AbstractPool<PooledObject<Session>> {

    private final Connection connection;
    private final MessageProducerPoolFactory producerPoolFactory;

    public SessionPool(int initialSize,
                       int maxSize,
                       Connection connection,
                       MessageProducerPoolFactory producerPoolFactory) {
        super(initialSize, maxSize);
        this.connection = connection;
        this.producerPoolFactory = producerPoolFactory;
    }

    @Override
    protected PooledObject<Session> create(Object... args) {
        try {
            Session session;
            if (args == null || args.length == 0) {
                session = connection.createSession();
            } else if (args.length == 1) {
                session = connection.createSession((Integer) args[0]);
            } else if (args.length == 2) {
                session = connection.createSession((Boolean) args[0], (Integer) args[1]);
            } else {
                throw new IllegalArgumentException(
                    "Unable to create a Session from arguments " + Arrays.toString(args));
            }

            return new PooledSession(this, session, producerPoolFactory.getProducerPool(session));
        } catch (JMSException | RuntimeException e) {
            throw new MessagingSystemException("Problem creating a Session", e);
        }
    }

    @Override
    protected void reset(PooledObject<Session> pooledObject) {
        // TODO:  implement sensible actions to reset a Session so it can be reused.
    }
}
