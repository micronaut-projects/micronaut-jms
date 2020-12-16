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

import javax.jms.Session;

/**
 * Factory for generating {@link MessageProducerPool} from a {@link Session}.
 *
 * @author Elliott Pope
 * @see MessageProducerPool
 * @see PooledSession
 * @see PooledProducer
 * @since 1.0.0
 */
@Context
public class MessageProducerPoolFactory {

    private static final int DEFAULT_POOL_INITIAL_SIZE = 1; // TODO configurable
    private static final int DEFAULT_POOL_MAX_SIZE = 20; // TODO configurable

    /**
     * Generates and configures a {@link MessageProducerPool} given a {@link Session}.
     *
     * @param session the session
     * @return a {@link MessageProducerPool} from the provided {@code session}
     */
    public MessageProducerPool getProducerPool(Session session) {
        return new MessageProducerPool(DEFAULT_POOL_INITIAL_SIZE, DEFAULT_POOL_MAX_SIZE, session);
    }
}
