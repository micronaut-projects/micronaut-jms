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
package io.micronaut.jms.activemq.configuration.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;

import javax.validation.constraints.NotBlank;

import static io.micronaut.jms.activemq.configuration.properties.ActiveMqConfigurationProperties.PREFIX;

/**
 * Configuration properties for creating the
 * {@link io.micronaut.jms.annotations.JMSConnectionFactory}.
 *
 * @author Elliott Pope
 * @see io.micronaut.jms.activemq.configuration.ActiveMqConfiguration
 * @since 1.0.0
 */
@ConfigurationProperties(PREFIX)
@Requires(property = PREFIX + ".enabled", value = "true")
public interface ActiveMqConfigurationProperties {

    /**
     * Prefix for ActiveMQ JMS settings.
     */
    String PREFIX = JMSConfigurationProperties.PREFIX + ".activemq";

    /**
     * Whether ActiveMQ is active.
     *
     * @return true to activate the ActiveMQ JMS implementation
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
