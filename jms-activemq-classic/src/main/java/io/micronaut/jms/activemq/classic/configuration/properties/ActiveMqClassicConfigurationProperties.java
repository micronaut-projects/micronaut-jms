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
package io.micronaut.jms.activemq.classic.configuration.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;

import javax.validation.constraints.NotBlank;

import static io.micronaut.jms.activemq.classic.configuration.properties.ActiveMqClassicConfigurationProperties.PREFIX;

/**
 * Configuration properties for creating the ActiveMQ Classic
 * {@link io.micronaut.jms.annotations.JMSConnectionFactory}.
 *
 * @author Elliott Pope
 * @see ActiveMqClassicConfiguration
 * @since 1.0.0
 */
@ConfigurationProperties(PREFIX)
@Requires(property = PREFIX + ".enabled", value = "true")
public interface ActiveMqClassicConfigurationProperties {

    /**
     * Prefix for ActiveMQ Classic JMS settings.
     */
    String PREFIX = JMSConfigurationProperties.PREFIX + ".activemq.classic"; // micronaut.jms.activemq.classic

    /**
     * Whether ActiveMQ Classic is active.
     *
     * @return true to activate the ActiveMQ Classic JMS implementation
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
}
