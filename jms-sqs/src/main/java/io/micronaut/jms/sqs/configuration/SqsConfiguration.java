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
package io.micronaut.jms.sqs.configuration;

import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.services.sqs.AmazonSQS;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import io.micronaut.jms.sqs.configuration.properties.SqsConfigurationProperties;

import javax.jms.ConnectionFactory;

import static io.micronaut.jms.sqs.configuration.properties.SqsConfigurationProperties.PREFIX;

/**
 * Generates the AWS SQS {@link JMSConnectionFactory} based on the properties
 * provided by {@link SqsConfigurationProperties}.
 *
 * @author Burt Beckwith
 * @since 1.0.0
 */
@Factory
@Requires(property = PREFIX + ".enabled", value = "true")
public class SqsConfiguration {

    public static final String CONNECTION_FACTORY_BEAN_NAME = "sqsJmsConnectionFactory";

    /**
     * Generates a {@link JMSConnectionFactory} bean in the application context.
     * <p>
     * The bean is a {@link SQSConnectionFactory}
     * configured with properties from {@link SqsConfigurationProperties}.
     *
     * @param config config settings for SQS
     * @param sqs    a configured AmazonSQS instance, typically built with
     *               {@link com.amazonaws.services.sqs.AmazonSQSClientBuilder}.
     * @return the {@link SQSConnectionFactory} defined by the {@code config}.
     */
    @JMSConnectionFactory(CONNECTION_FACTORY_BEAN_NAME)
    public ConnectionFactory sqsJmsConnectionFactory(SqsConfigurationProperties config,
                                                     AmazonSQS sqs) {
        return new SQSConnectionFactory(
            new ProviderConfiguration().withNumberOfMessagesToPrefetch(config.getNumberOfMessagesToPrefetch()),
            sqs);
    }
}
