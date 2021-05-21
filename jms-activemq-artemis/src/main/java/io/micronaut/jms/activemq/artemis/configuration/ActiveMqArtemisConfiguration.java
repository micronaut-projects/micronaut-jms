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
package io.micronaut.jms.activemq.artemis.configuration;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jms.activemq.artemis.configuration.properties.ActiveMqArtemisConfigurationProperties;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.ConnectionFactory;

import static io.micronaut.jms.activemq.artemis.configuration.properties.ActiveMqArtemisConfigurationProperties.PREFIX;

/**
 * Generates the ActiveMQ Artemis {@link JMSConnectionFactory} based on the
 * properties provided by {@link ActiveMqArtemisConfigurationProperties}.
 *
 * @author Burt Beckwith
 * @since 1.0.0
 */
@Factory
@Requires(property = PREFIX + ".enabled", value = "true")
public class ActiveMqArtemisConfiguration {

    /**
     * Name of the ActiveMQ Artemis {@link ConnectionFactory} bean.
     */
    public static final String CONNECTION_FACTORY_BEAN_NAME = "activeMqArtemisConnectionFactory";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Generates a {@link JMSConnectionFactory} bean in the application context.
     * <p>
     * The bean is a {@link ActiveMQJMSConnectionFactory} configured with
     * properties from {@link ActiveMqArtemisConfigurationProperties}.
     *
     * @param config config settings for ActiveMQ Artemis
     * @return the {@link ActiveMQJMSConnectionFactory} defined by the {@code config}.
     */
    @JMSConnectionFactory(CONNECTION_FACTORY_BEAN_NAME)
    public ConnectionFactory activeMqArtemisConnectionFactory(ActiveMqArtemisConfigurationProperties config) {
        logger.debug("created ConnectionFactory bean '{}' (ActiveMQJMSConnectionFactory) for broker URL '{}'",
                CONNECTION_FACTORY_BEAN_NAME, config.getConnectionString());

        String username = config.getUsername();
        String password = config.getPassword();
        if (StringUtils.isNotEmpty(username) || StringUtils.isNotEmpty(password)) {
            return new ActiveMQJMSConnectionFactory(config.getConnectionString(), username, password);
        }
        return new ActiveMQJMSConnectionFactory(config.getConnectionString());
    }
}
