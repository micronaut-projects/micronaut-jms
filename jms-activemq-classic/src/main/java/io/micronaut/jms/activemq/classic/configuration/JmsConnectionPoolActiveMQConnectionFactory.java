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
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.exceptions.ConfigurationException;
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
     * The pool sizes are received as values rather than through {@link JMSConfigurationProperties}, so that the pool
     * holds nothing of the context that created it, and development mode can keep it, with its connections, across a
     * restart of the application. A change under {@value JMSConfigurationProperties#PREFIX} releases it.
     *
     * @param connectionFactory Connection Factory
     * @param initialPoolSize The initial pool size, {@link JMSConfigurationProperties#getInitialPoolSize()}
     * @param maxPoolSize The maximum pool size, {@link JMSConfigurationProperties#getMaxPoolSize()}
     * @return JMS Connection Pool
     */
    @EachBean(ActiveMQConnectionFactory.class)
    @Singleton
    @Retain(invalidatedBy = JMSConfigurationProperties.PREFIX)
    JMSConnectionPool createJmsConnectionPool(ActiveMQConnectionFactory connectionFactory,
                                              @Property(name = JMSConfigurationProperties.PREFIX + ".initial-pool-size", defaultValue = "1") int initialPoolSize,
                                              @Property(name = JMSConfigurationProperties.PREFIX + ".max-pool-size", defaultValue = "50") int maxPoolSize) {
        // the constraints of JMSConfigurationProperties, which is no longer received
        requireAtLeastOne("initial-pool-size", initialPoolSize);
        requireAtLeastOne("max-pool-size", maxPoolSize);
        return new JMSConnectionPool(connectionFactory, initialPoolSize, maxPoolSize);
    }

    private static void requireAtLeastOne(String name, int value) {
        if (value < 1) {
            throw new ConfigurationException(JMSConfigurationProperties.PREFIX + "." + name + " must be at least 1, but was " + value);
        }
    }
}
