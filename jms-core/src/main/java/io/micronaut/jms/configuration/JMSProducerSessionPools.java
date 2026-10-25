/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.jms.configuration;

import io.micronaut.core.annotation.Internal;
import io.micronaut.jms.configuration.properties.JMSProducerSessionConfiguration;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.pool.ProducerSessionPool;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The session pools of producer methods, one for each connection factory, closed with the application context.
 *
 * @since 5.2.0
 */
@Internal
@Singleton
final class JMSProducerSessionPools {

    private final JMSProducerSessionConfiguration configuration;
    private final Map<String, ProducerSessionPool> pools = new ConcurrentHashMap<>();

    JMSProducerSessionPools(JMSProducerSessionConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * @param connectionFactory The name of the connection factory
     * @param connectionPool    Its connection pool
     * @return The session pool, or {@code null} if sessions are not reused
     */
    @Nullable ProducerSessionPool get(String connectionFactory, JMSConnectionPool connectionPool) {
        if (!configuration.isEnabled()) {
            return null;
        }
        return pools.computeIfAbsent(connectionFactory,
            name -> new ProducerSessionPool(connectionPool, configuration.getMaxIdle()));
    }

    @PreDestroy
    void close() {
        pools.values().forEach(ProducerSessionPool::close);
        pools.clear();
    }
}
