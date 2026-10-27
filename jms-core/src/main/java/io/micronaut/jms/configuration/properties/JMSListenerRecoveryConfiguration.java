/*
 * Copyright 2017-2026 original authors
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
 * The configuration of how listeners recover when the JMS provider reports that the session of a consumer failed.
 * When the connection of a listener reports an error, each of its consumers checks its session, and a consumer whose
 * session no longer works closes its session and consumer and creates them again on the same connection.
 *
 * @since 5.2.0
 */
@ConfigurationProperties(JMSListenerRecoveryConfiguration.PREFIX)
public interface JMSListenerRecoveryConfiguration {

    /**
     * The prefix of the configuration.
     */
    String PREFIX = JMSConfigurationProperties.PREFIX + ".listener-recovery";

    /**
     * @return Whether listeners recreate a session that the JMS provider reports as failed. Defaults to {@code true}.
     */
    @Bindable(defaultValue = "true")
    boolean isEnabled();

    /**
     * @return The number of times a consumer tries to recreate its session before it gives up until the provider
     * reports another error. Defaults to 10.
     */
    @Min(1)
    @Bindable(defaultValue = "10")
    int getMaxAttempts();

    /**
     * @return How long a consumer waits after a failed attempt before the next one. The wait doubles after each
     * attempt, up to {@link #getMaxDelay()}. Defaults to 1 second.
     */
    @Bindable(defaultValue = "1s")
    Duration getInitialDelay();

    /**
     * @return The longest wait between two attempts. Defaults to 1 minute.
     */
    @Bindable(defaultValue = "1m")
    Duration getMaxDelay();
}
