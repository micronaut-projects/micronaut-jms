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

import io.micronaut.context.annotation.Context;

import javax.jms.Connection;

/**
 * Factory for generating a {@link SessionPool} from a {@link Connection}.
 *
 * @author Elliott Pope
 * @see JMSConnectionPool
 * @see PooledConnection
 * @since 1.0.0
 */
@Context
public class SessionPoolFactory {

    private static final int DEFAULT_POOL_INITIAL_SIZE = 1; // TODO configurable
    private static final int DEFAULT_POOL_MAX_SIZE = 20; // TODO configurable

    private final MessageProducerPoolFactory producerPoolFactory;

    public SessionPoolFactory(MessageProducerPoolFactory producerPoolFactory) {
        this.producerPoolFactory = producerPoolFactory;
    }

    /**
     * Returns a {@link SessionPool} from the provided {@code connection}.
     *
     * @param connection the connection
     * @return a {@link SessionPool} from the provided {@code connection}.
     */
    public SessionPool getSessionPool(Connection connection) {
        return new SessionPool(DEFAULT_POOL_INITIAL_SIZE, DEFAULT_POOL_MAX_SIZE, connection, producerPoolFactory);
    }
}
