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
package io.micronaut.jms.sqs.configuration;

import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Retain;
import io.micronaut.core.annotation.Internal;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.inject.Singleton;

@Factory
@Internal
class JmsConnectionPoolSQSConnectionFactory {

    /**
     * Creates a {@link JMSConnectionPool} from each registered {@link SQSConnectionFactory} in the context.
     * The configuration is received by the method rather than held by the factory, so that the factory and the pool
     * keep only the pool sizes they copied, as development mode retains them across a restart until a change under
     * {@value JMSConfigurationProperties#PREFIX} or {@value SqsConfiguration#AWS_PREFIX}.
     * @param connectionFactory Connection Factory
     * @param properties JMS Configuration
     * @return JMS Connection Pool
     */
    @EachBean(SQSConnectionFactory.class)
    @Singleton
    @Retain(invalidatedBy = {JMSConfigurationProperties.PREFIX, SqsConfiguration.AWS_PREFIX})
    JMSConnectionPool createJmsConnectionPool(SQSConnectionFactory connectionFactory, JMSConfigurationProperties properties) {
        return new JMSConnectionPool(connectionFactory, properties.getInitialPoolSize(), properties.getMaxPoolSize());
    }
}
