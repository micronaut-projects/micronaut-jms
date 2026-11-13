/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.jms.activemq.classic.configuration;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Retain;
import io.micronaut.core.annotation.Internal;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.inject.Singleton;
import org.apache.activemq.ActiveMQConnectionFactory;

@Factory
@Internal
class JmsConnectionPoolActiveMQConnectionFactory {
    /**
     * Creates a {@link JMSConnectionPool} from each registered {@link ActiveMQConnectionFactory} in the context.
     * The configuration is received by the method rather than held by the factory, so that the factory and the pool
     * keep only the pool sizes they copied, as development mode retains them across a restart until a change under
     * {@value JMSConfigurationProperties#PREFIX}.
     * @param connectionFactory Connection Factory
     * @param properties JMS Configuration
     * @return JMS Connection Pool
     */
    @EachBean(ActiveMQConnectionFactory.class)
    @Singleton
    @Retain(invalidatedBy = JMSConfigurationProperties.PREFIX)
    JMSConnectionPool createJmsConnectionPool(ActiveMQConnectionFactory connectionFactory, JMSConfigurationProperties properties) {
        return new JMSConnectionPool(connectionFactory, properties.getInitialPoolSize(), properties.getMaxPoolSize());
    }
}
