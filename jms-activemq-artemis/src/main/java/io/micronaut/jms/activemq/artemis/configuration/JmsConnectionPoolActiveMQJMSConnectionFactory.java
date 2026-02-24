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
package io.micronaut.jms.activemq.artemis.configuration;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.inject.Singleton;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;

@Factory
@Internal
class JmsConnectionPoolActiveMQJMSConnectionFactory {
    private final JMSConfigurationProperties properties;

    /**
     * @param properties JMS Configuration
     */
    JmsConnectionPoolActiveMQJMSConnectionFactory(JMSConfigurationProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates a {@link JMSConnectionPool} from each registered {@link ActiveMQJMSConnectionFactory} in the context.
     * @param connectionFactory Connection Factory
     * @return JMS Connection Pool
     */
    @EachBean(ActiveMQJMSConnectionFactory.class)
    @Singleton
    JMSConnectionPool createJmsConnectionPool(ActiveMQJMSConnectionFactory connectionFactory) {
        return new JMSConnectionPool(connectionFactory, properties.getInitialPoolSize(), properties.getMaxPoolSize());
    }
}
