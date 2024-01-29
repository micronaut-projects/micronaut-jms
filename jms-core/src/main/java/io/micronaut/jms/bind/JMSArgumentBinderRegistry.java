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
package io.micronaut.jms.bind;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.bind.annotation.AnnotatedArgumentBinder;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.jms.serdes.Deserializer;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.Message;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * An {@link ArgumentBinderRegistry} for all implementations of
 * {@link ArgumentBinder} capable of binding a {@link Message}.
 *
 * @author Elliott Pope
 * @see DefaultHeaderArgumentBinder
 * @see DefaultBodyArgumentBinder
 * @see io.micronaut.jms.configuration.AbstractJMSListenerMethodProcessor
 * @since 1.0.0
 */
@Internal
@Singleton
public class JMSArgumentBinderRegistry implements ArgumentBinderRegistry<Message> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<AnnotatedArgumentBinder<?, ?, ?>> binders = new LinkedList<>();

    public JMSArgumentBinderRegistry(ConversionService conversionService, Deserializer deserializer) {
        registerArgumentBinder(new DefaultBodyArgumentBinder(conversionService, deserializer));
        registerArgumentBinder(new MessageBodyHeaderArgumentBinder(conversionService, deserializer));
        registerArgumentBinder(new DefaultHeaderArgumentBinder(conversionService));
        registerArgumentBinder(new DefaultMessageArgumentBinder(conversionService));
    }

    /**
     * Registers an {@link ArgumentBinder}. Implement {@link io.micronaut.core.order.Ordered}
     * to override the default binder for the parameter annotation type.
     *
     * @param binder the binder
     */
    public void registerArgumentBinder(AnnotatedArgumentBinder<?, ?, ?> binder) {
        binders.add(binder);
        binders.sort(OrderUtil.COMPARATOR);
        logger.debug("registered binder {}", binder);
    }

    /**
     * Remove a registered binder. Primarily for testing.
     *
     * @param binder the binder to remove
     */
    public void unregisterArgumentBinder(AnnotatedArgumentBinder<?, ?, ?> binder) {
        binders.remove(binder);
        logger.debug("unregistered binder {}", binder);
    }

    @Override
    public <T> Optional<ArgumentBinder<T, Message>> findArgumentBinder(Argument<T> argument) {
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        for (AnnotatedArgumentBinder<?, ?, ?> binder: binders) {
            if (annotationMetadata.hasAnnotation(binder.getAnnotationType())) {
                return (Optional) Optional.of(binder);
            }
        }
        throw new IllegalArgumentException("Cannot bind argument " + argument.getName());
    }

    @Override
    public String toString() {
        return "JMSArgumentBinderRegistry{binders=" + binders + '}';
    }
}
