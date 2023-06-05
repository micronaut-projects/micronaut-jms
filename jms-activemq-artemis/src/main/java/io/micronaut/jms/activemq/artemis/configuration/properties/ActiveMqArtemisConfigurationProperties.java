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
package io.micronaut.jms.activemq.artemis.configuration.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;

import jakarta.validation.constraints.NotBlank;

import static io.micronaut.jms.activemq.artemis.configuration.properties.ActiveMqArtemisConfigurationProperties.PREFIX;

/**
 * Configuration properties for creating the ActiveMQ Artemis
 * {@link io.micronaut.jms.annotations.JMSConnectionFactory}.
 *
 * @author Burt Beckwith
 * @see io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration
 * @since 1.0.0
 */
@ConfigurationProperties(PREFIX)
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
public interface ActiveMqArtemisConfigurationProperties {

    /**
     * Prefix for ActiveMQ Artemis JMS settings.
     */
    String PREFIX = JMSConfigurationProperties.PREFIX + ".activemq.artemis"; // micronaut.jms.activemq.artemis

    /**
     * Whether ActiveMQ Artemis is active.
     *
     * @return true to activate the ActiveMQ Artemis JMS implementation
     */
    boolean isEnabled();

    /**
     * The connection string to identify the broker URL,
     * e.g. vm://localhost or tcp://my-activemq-cluster:61616.
     *
     * @return the connection string
     */
    @NotBlank
    String getConnectionString();

    /**
     * The username.
     *
     * @return the username
     */
    @Nullable
    String getUsername();

    /**
     * The password.
     *
     * @return the password
     */
    @Nullable
    String getPassword();
}
