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
 * session no longer works closes its session and consumer and creates them again on the same connection. A failed
 * attempt is retried, with a growing delay, until it succeeds or the listener shuts down.
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
     * Whether recovery is enabled by default.
     */
    boolean DEFAULT_ENABLED = true;

    /**
     * The default number of failed attempts logged as warnings.
     */
    int DEFAULT_WARN_ATTEMPTS = 10;

    /**
     * The default delay after the first failed attempt.
     */
    String DEFAULT_INITIAL_DELAY = "PT1S";

    /**
     * The default longest delay between two attempts.
     */
    String DEFAULT_MAX_DELAY = "PT1M";

    /**
     * @return Whether listeners recreate a session that the JMS provider reports as failed. Defaults to {@code true}.
     */
    @Bindable(defaultValue = "" + DEFAULT_ENABLED)
    boolean isEnabled();

    /**
     * @return The number of failed attempts to recreate a session that are logged as warnings. Later attempts are
     * logged as errors, and continue until one succeeds or the listener shuts down. Defaults to 10.
     */
    @Min(1)
    @Bindable(defaultValue = "" + DEFAULT_WARN_ATTEMPTS)
    int getWarnAttempts();

    /**
     * @return How long a consumer waits after a failed attempt before the next one. The wait doubles after each
     * attempt, up to {@link #getMaxDelay()}. It must be positive. Defaults to 1 second.
     */
    @Bindable(defaultValue = DEFAULT_INITIAL_DELAY)
    Duration getInitialDelay();

    /**
     * @return The longest wait between two attempts, which every attempt waits once the doubling reaches it. It must not
     * be shorter than {@link #getInitialDelay()}. Defaults to 1 minute.
     */
    @Bindable(defaultValue = DEFAULT_MAX_DELAY)
    Duration getMaxDelay();
}
