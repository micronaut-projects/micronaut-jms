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
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jms.activemq.artemis.configuration.properties.ActiveMqArtemisConfigurationProperties;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.ConnectionFactory;

import static io.micronaut.jms.activemq.artemis.configuration.properties.ActiveMqArtemisConfigurationProperties.PREFIX;

/**
 * Generates the ActiveMQ Artemis {@link JMSConnectionFactory} based on the
 * properties provided by {@link ActiveMqArtemisConfigurationProperties}.
 *
 * @author Burt Beckwith
 * @since 1.0.0
 */
@Factory
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
public class ActiveMqArtemisConfiguration {

    /**
     * Name of the ActiveMQ Artemis {@link ConnectionFactory} bean.
     */
    public static final String CONNECTION_FACTORY_BEAN_NAME = "activeMqArtemisConnectionFactory";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Generates a {@link JMSConnectionFactory} bean in the application context.
     * <p>
     * The bean is a {@link ActiveMQJMSConnectionFactory} configured with the properties under
     * {@value ActiveMqArtemisConfigurationProperties#PREFIX}. The properties are received as values rather
     * than through {@link ActiveMqArtemisConfigurationProperties}, so that the connection factory holds
     * nothing of the context that created it, and development mode can keep it, and the connection pool built on
     * it, across a restart of the application. A change under {@value JMSConfigurationProperties#PREFIX} releases it.
     *
     * @param connectionString the broker URL
     * @param username the username, if any
     * @param password the password, if any
     * @return the {@link ActiveMQJMSConnectionFactory}
     * @since 5.2.0
     */
    @JMSConnectionFactory(CONNECTION_FACTORY_BEAN_NAME)
    @Retain(invalidatedBy = JMSConfigurationProperties.PREFIX)
    public ActiveMQJMSConnectionFactory activeMqArtemisConnectionFactory(@Property(name = PREFIX + ".connection-string") String connectionString,
                                                                         @Property(name = PREFIX + ".username") @Nullable String username,
                                                                         @Property(name = PREFIX + ".password") @Nullable String password) {
        logger.debug("created ConnectionFactory bean '{}' (ActiveMQJMSConnectionFactory) for broker URL '{}'",
                CONNECTION_FACTORY_BEAN_NAME, connectionString);
        if (StringUtils.isNotEmpty(username) || StringUtils.isNotEmpty(password)) {
            return new ActiveMQJMSConnectionFactory(connectionString, username, password);
        }
        return new ActiveMQJMSConnectionFactory(connectionString);
    }

    /**
     * Generates a {@link JMSConnectionFactory} bean in the application context.
     * <p>
     * The bean is a {@link ActiveMQJMSConnectionFactory} configured with
     * properties from {@link ActiveMqArtemisConfigurationProperties}.
     *
     * @param config config settings for ActiveMQ Artemis
     * @return the {@link ActiveMQJMSConnectionFactory} defined by the {@code config}.
     * @deprecated The bean is created by {@link #activeMqArtemisConnectionFactory(String, String, String)}, from the same properties
     */
    @Deprecated(since = "5.2.0")
    public ActiveMQJMSConnectionFactory activeMqArtemisConnectionFactory(ActiveMqArtemisConfigurationProperties config) {
        return activeMqArtemisConnectionFactory(config.getConnectionString(), config.getUsername(), config.getPassword());
    }
}
