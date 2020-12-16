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

import javax.validation.constraints.NotBlank;

/***
 * Configuration properties for creating the {@link io.micronaut.jms.annotations.JMSConnectionFactory}.
 *
 * @see io.micronaut.jms.activemq.configuration.ActiveMqConfiguration
 *
 * @author elliott
 * @since 1.0
 */
@ConfigurationProperties("micronaut.jms.activemq")
@Requires(property = "micronaut.jms.activemq.enabled", value = "true")
public interface ActiveMqConfigurationProperties {

    /***
     * @return true if the Micronaut JMS ActiveMQ implementation should be activated.
     *      This will allow you to inject a {@link javax.jms.ConnectionFactory} bean
     *      with name "activeMqConnectionFactory" into your {@link io.micronaut.jms.annotations.JMSListener}
     *      and {@link io.micronaut.jms.annotations.JMSProducer} implementations.
     */
    boolean isEnabled();

    /***
     * @return connection string to identify the broker URL i.e. vm://localhost, or tcp://my-activemq-cluster:61616
     */
    @NotBlank
    String getConnectionString();
}
