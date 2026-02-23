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
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;
import io.micronaut.jms.pool.JMSConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import jakarta.inject.Named;
import jakarta.jms.ConnectionFactory;

/**
 * Bean creation listener that registers a {@link JMSConnectionPool} for each
 * {@link ConnectionFactory} bean annotated with {@link JMSConnectionFactory}.
 * This guarantees that the pool is created at the moment the underlying
 * {@link ConnectionFactory} is created, making the pool available for
 * {@code @JMSListener} registration irrespective of processor ordering.
 *
 * The pool is registered with the same {@code @Named} qualifier as the
 * annotated {@link ConnectionFactory}.
 */
@Singleton
public final class JMSConnectionFactoryCreatedListener implements BeanCreatedEventListener<ConnectionFactory> {

    private static final Logger LOG = LoggerFactory.getLogger(JMSConnectionFactoryCreatedListener.class);

    private final BeanContext beanContext;
    private final JMSConfigurationProperties properties;

    public JMSConnectionFactoryCreatedListener(BeanContext beanContext,
                                               JMSConfigurationProperties properties) {
        this.beanContext = beanContext;
        this.properties = properties;
    }

    @Override
    public ConnectionFactory onCreated(BeanCreatedEvent<ConnectionFactory> event) {
        final BeanDefinition<ConnectionFactory> definition = event.getBeanDefinition();

        // Determine the bean name either from @JMSConnectionFactory or fallback to @Named
        final String name = definition.stringValue(JMSConnectionFactory.class).orElse(null);
        if (name == null) {
            // Not a named ConnectionFactory relevant to JMS; nothing to do
            return event.getBean();
        }

        // Avoid duplicate registration if another component already registered it
        if (beanContext.findBean(JMSConnectionPool.class, Qualifiers.byName(name)).isPresent()) {
            if (LOG.isInfoEnabled()) {
                LOG.info("JMSConnectionPool bean '{}' already registered. Skipping registration.", name);
            }
            return event.getBean();
        }

        // Register the pool with the same qualifier
        JMSConnectionPool pool = new JMSConnectionPool(
            event.getBean(),
            properties.getInitialPoolSize(),
            properties.getMaxPoolSize()
        );
        beanContext.registerSingleton(JMSConnectionPool.class, pool, Qualifiers.byName(name));

        if (LOG.isInfoEnabled()) {
            LOG.info("Registered JMSConnectionPool bean '{}' for ConnectionFactory {}", name, event.getBean().getClass().getName());
        }

        return event.getBean();
    }
}
