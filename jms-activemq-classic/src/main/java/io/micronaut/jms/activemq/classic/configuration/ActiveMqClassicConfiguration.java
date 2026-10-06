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
package io.micronaut.jms.activemq.classic.configuration;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Retain;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jms.activemq.classic.configuration.properties.ActiveMqClassicConfigurationProperties;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.ConnectionFactory;

import static io.micronaut.jms.activemq.classic.configuration.properties.ActiveMqClassicConfigurationProperties.PREFIX;

/**
 * Generates the ActiveMQ Classic {@link JMSConnectionFactory} based on the
 * properties provided by {@link ActiveMqClassicConfigurationProperties}.
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
@Factory
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
public class ActiveMqClassicConfiguration {

    /**
     * Name of the ActiveMQ Classic {@link ConnectionFactory} bean.
     */
    public static final String CONNECTION_FACTORY_BEAN_NAME = "activeMqConnectionFactory";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Generates a {@link JMSConnectionFactory} bean in the application context.
     * <p>
     * The bean is a {@link ActiveMQConnectionFactory} configured with the properties under
     * {@value ActiveMqClassicConfigurationProperties#PREFIX}. The properties are received as values rather
     * than through {@link ActiveMqClassicConfigurationProperties}, so that the connection factory holds
     * nothing of the context that created it, and development mode can keep it, and the connection pool built on
     * it, across a restart of the application. A change under {@value JMSConfigurationProperties#PREFIX} releases it.
     *
     * @param connectionString the broker URL
     * @param username the username, if any
     * @param password the password, if any
     * @return the {@link ActiveMQConnectionFactory}
     * @since 5.2.0
     */
    @JMSConnectionFactory(CONNECTION_FACTORY_BEAN_NAME)
    @Retain(invalidatedBy = JMSConfigurationProperties.PREFIX)
    public ActiveMQConnectionFactory activeMqConnectionFactory(@Property(name = PREFIX + ".connection-string") String connectionString,
                                                               @Property(name = PREFIX + ".username") @Nullable String username,
                                                               @Property(name = PREFIX + ".password") @Nullable String password) {
        logger.debug("created ConnectionFactory bean '{}' (ActiveMQConnectionFactory) for broker URL '{}'",
                CONNECTION_FACTORY_BEAN_NAME, connectionString);
        if (StringUtils.isEmpty(connectionString) || connectionString.isBlank()) {
            // the constraint of the configuration properties, which are no longer received
            throw new ConfigurationException(PREFIX + ".connection-string must not be blank");
        }
        if (StringUtils.isNotEmpty(username) || StringUtils.isNotEmpty(password)) {
            return new ActiveMQConnectionFactory(username, password, connectionString);
        }
        return new ActiveMQConnectionFactory(connectionString);
    }

    /**
     * Generates a {@link JMSConnectionFactory} bean in the application context.
     * <p>
     * The bean is a {@link ActiveMQConnectionFactory} configured with
     * properties from {@link ActiveMqClassicConfigurationProperties}.
     *
     * @param config config settings for ActiveMQ Classic
     * @return the {@link ActiveMQConnectionFactory} defined by the {@code config}.
     * @deprecated The bean is created by {@link #activeMqConnectionFactory(String, String, String)}, from the same properties
     */
    @Deprecated(since = "5.2.0")
    public ActiveMQConnectionFactory activeMqConnectionFactory(ActiveMqClassicConfigurationProperties config) {
        return activeMqConnectionFactory(config.getConnectionString(), config.getUsername(), config.getPassword());
    }
}
