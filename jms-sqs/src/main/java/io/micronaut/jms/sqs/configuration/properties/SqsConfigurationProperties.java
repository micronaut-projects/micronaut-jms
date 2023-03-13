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
package io.micronaut.jms.sqs.configuration.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import static com.amazon.sqs.javamessaging.SQSMessagingClientConstants.MIN_PREFETCH;
import static io.micronaut.jms.sqs.configuration.properties.SqsConfigurationProperties.PREFIX;

/**
 * Configuration properties for creating the AWS SQS
 * {@link io.micronaut.jms.annotations.JMSConnectionFactory}.
 *
 * @author Burt Beckwith
 * @see io.micronaut.jms.sqs.configuration.SqsConfiguration
 * @since 1.0.0
 */
@ConfigurationProperties(PREFIX)
@Requires(property = PREFIX + ".enabled", value = "true")
public interface SqsConfigurationProperties {

    /**
     * Prefix for SQS JMS settings.
     */
    String PREFIX = JMSConfigurationProperties.PREFIX + ".sqs"; // micronaut.jms.sqs

    /**
     * Whether SQS is active.
     *
     * @return true to activate the SQS JMS implementation
     */
    boolean isEnabled();

    /**
     * Number of messages to prefetch for better receive turnaround times.
     *
     * @return the prefetch count
     */
    @NotNull
    @Min(0)
    @Bindable(defaultValue = "" + MIN_PREFETCH)
    Integer getNumberOfMessagesToPrefetch();
}
