/*
 * Copyright 2017-2022 original authors
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
import jakarta.validation.constraints.Min;

import java.time.Duration;

/**
 * The configuration of the sessions that producer methods send with.
 *
 * @since 5.2.0
 */
@ConfigurationProperties(JMSProducerSessionConfiguration.PREFIX)
public interface JMSProducerSessionConfiguration {

    /**
     * The prefix of the configuration.
     */
    String PREFIX = JMSConfigurationProperties.PREFIX + ".producer-sessions";

    /**
     * @return Whether producer methods reuse sessions, on one connection of each connection factory. When disabled,
     * each send takes a connection from the pool and opens a session. Defaults to {@code true}.
     */
    @Bindable(defaultValue = "true")
    boolean isEnabled();

    /**
     * @return The maximum number of idle sessions kept for each connection factory and session mode. Defaults to 8.
     */
    @Min(0)
    @Bindable(defaultValue = "8")
    int getMaxIdle();

    /**
     * @return The maximum number of open sessions for each connection factory, borrowed or idle, of all session
     * modes. When all of them are borrowed, a send waits up to {@link #getMaxWait()} for one to be released.
     * Defaults to 32.
     * @since 5.2.0
     */
    @Min(1)
    @Bindable(defaultValue = "32")
    int getMaxActive();

    /**
     * @return How long a send waits for a session when {@link #getMaxActive()} sessions are borrowed, after which the
     * send fails. Defaults to 10 seconds.
     * @since 5.2.0
     */
    @Bindable(defaultValue = "10s")
    Duration getMaxWait();
}
