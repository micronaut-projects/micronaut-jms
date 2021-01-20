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
package io.micronaut.jms.configuration.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import static io.micronaut.jms.configuration.properties.JMSConfigurationProperties.PREFIX;

/**
 * Generic configuration properties for global Micronaut JMS properties.
 * <p>
 * Existing properties include:
 * - initialPoolSize: the default size of the {@link io.micronaut.jms.pool.JMSConnectionPool},
 * {@link io.micronaut.jms.pool.SessionPool}, and {@link io.micronaut.jms.pool.MessageProducerPool}.
 * - maxPoolSize: the maximum size of the {@link io.micronaut.jms.pool.JMSConnectionPool},
 * {@link io.micronaut.jms.pool.SessionPool}, and {@link io.micronaut.jms.pool.MessageProducerPool}.
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
@ConfigurationProperties(PREFIX)
public interface JMSConfigurationProperties {

    /**
     * Prefix for JMS settings.
     */
    String PREFIX = "micronaut.jms";

    /**
     * The initial size of the {@link io.micronaut.jms.pool.JMSConnectionPool},
     * {@link io.micronaut.jms.pool.SessionPool}, and {@link io.micronaut.jms.pool.MessageProducerPool}.
     *
     * @return the initial size
     */
    @NotNull
    @Min(1)
    @Bindable(defaultValue = "1")
    Integer getInitialPoolSize();

    /**
     * The maximum size of the {@link io.micronaut.jms.pool.JMSConnectionPool},
     * {@link io.micronaut.jms.pool.SessionPool}, and {@link io.micronaut.jms.pool.MessageProducerPool}.
     *
     * @return the maximum size
     */
    @NotNull
    @Min(1)
    @Bindable(defaultValue = "50")
    Integer getMaxPoolSize();
}
