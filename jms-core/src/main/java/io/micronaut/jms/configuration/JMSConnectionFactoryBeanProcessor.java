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
package io.micronaut.jms.configuration;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.processor.BeanDefinitionProcessor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.util.Assert;

import javax.jms.ConnectionFactory;

/***
 *
 * Factory for generating {@link JMSConnectionPool} from each registered {@link ConnectionFactory} in the context.
 *
 * @author elliott
 * @since 1.0
 */
@Context
@Factory
public class JMSConnectionFactoryBeanProcessor implements BeanDefinitionProcessor<JMSConnectionFactory> {

    private final JMSConfigurationProperties properties;

    public JMSConnectionFactoryBeanProcessor(JMSConfigurationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, BeanContext context) {
        final Object candidate = context.getBean(beanDefinition);
        Assert.isTrue(candidate instanceof ConnectionFactory,
            () -> "@JMSConnectionFactory can only be applied to a bean of type javax.jms.ConnectionFactory. " +
                "Provided class was " + candidate.getClass().getName());

        final ConnectionFactory connectionFactory = (ConnectionFactory) candidate;
        final String name = beanDefinition.stringValue(JMSConnectionFactory.class)
            .orElseThrow(() -> new ConfigurationException(
                "@JMSConnectionFactory must specify a name for the bean."));

        context.registerSingleton(
            JMSConnectionPool.class,
            new JMSConnectionPool(
                connectionFactory,
                properties.getInitialPoolSize(),
                properties.getMaxPoolSize()),
            Qualifiers.byName(name));
    }
}
