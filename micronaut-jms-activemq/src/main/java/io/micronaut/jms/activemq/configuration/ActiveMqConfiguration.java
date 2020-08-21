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
package io.micronaut.jms.activemq.configuration;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.activemq.configuration.properties.ActiveMqConfigurationProperties;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.ConnectionFactory;

/***
 * Factory for generating the ActiveMQ {@link JMSConnectionFactory} based on the configuration properties
 *      provided by {@link ActiveMqConfigurationProperties}.
 *
 * @since 1.0
 * @author elliott
 */
@Factory
@Requires(property = "micronaut.jms.activemq.enabled", value = "true")
public class ActiveMqConfiguration {

    /***
     *
     * Generates a {@link JMSConnectionFactory} bean in the application context.
     *
     * The bean is a simply configured {@link ActiveMQConnectionFactory} configured with properties
     *      from {@link ActiveMqConfigurationProperties}
     *
     * @param configuration
     *
     * @return the {@link ActiveMQConnectionFactory} defined by the {@param configuration}
     */
    @JMSConnectionFactory("activeMqConnectionFactory")
    public ConnectionFactory activeMqConnectionFactory(ActiveMqConfigurationProperties configuration) {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(configuration.getConnectionString());
        return connectionFactory;
    }
}
