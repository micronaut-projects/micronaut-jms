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
import io.micronaut.core.annotation.Internal;

import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;

/**
 * Eagerly initializes all {@link ConnectionFactory} beans so that
 * {@link JMSConnectionFactoryCreatedListener} can register matching
 * {@link io.micronaut.jms.pool.JMSConnectionPool} instances before
 * {@code @JMSListener} methods are processed.
 * <p>
 * This restores the effective behavior of eagerly making the pools
 * available at startup, independent of processor execution ordering.
 *
 * This bean is internal to the Micronaut JMS module.
 */
@Internal
@Context
@Singleton
public final class JMSConnectionFactoriesEagerInitializer {

    JMSConnectionFactoriesEagerInitializer(BeanContext beanContext,
                                           JMSConnectionFactoryCreatedListener listener) {
        // Ensure the BeanCreatedEventListener is initialized before creating CFs
        // Then trigger creation of all ConnectionFactory beans (e.g. those annotated with @JMSConnectionFactory)
        // which in turn allows the BeanCreatedEventListener to register the corresponding pools.
        beanContext.getBeansOfType(ConnectionFactory.class);
    }
}
