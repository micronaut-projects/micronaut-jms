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

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;

import javax.inject.Singleton;
import javax.jms.Message;
import java.lang.annotation.Annotation;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * An {@link ArgumentBinderRegistry} for all implementations of
 * {@link ArgumentBinder} capable of binding a {@link Message}.
 *
 * @author Elliott Pope
 * @see HeaderArgumentBinder
 * @see BodyArgumentBinder
 * @see io.micronaut.jms.configuration.AbstractJMSListenerMethodProcessor
 * @since 1.0.0
 */
@Singleton
public class JMSArgumentBinderRegistry implements ArgumentBinderRegistry<Message> {

    private final List<AbstractAnnotatedArgumentBinder<?, ?, Message>> binders = new LinkedList<>();

    public JMSArgumentBinderRegistry(ConversionService<?> conversionService) {
        registerArgumentBinder(new BodyArgumentBinder(conversionService));
        registerArgumentBinder(new HeaderArgumentBinder(conversionService));
        registerArgumentBinder(new DefaultMessageArgumentBinder(conversionService));
    }

    /**
     * Registers an {@link ArgumentBinder}. Implement {@link io.micronaut.core.order.Ordered}
     * to override the default binder for the parameter annotation type.
     *
     * @param binder the binder
     */
    public void registerArgumentBinder(AbstractAnnotatedArgumentBinder<?, ?, Message> binder) {
        binders.add(binder);
    }

    /**
     * Remove a registered binder. Primarily for testing.
     *
     * @param binder the binder to remove
     */
    public void unregisterArgumentBinder(AbstractAnnotatedArgumentBinder<?, ?, Message> binder) {
        binders.remove(binder);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<ArgumentBinder<T, Message>> findArgumentBinder(Argument<T> argument,
                                                                       Message source) {
        Optional<Class<? extends Annotation>> opt =
            argument.getAnnotationMetadata().getAnnotationTypeByStereotype(Bindable.class);
        if (!opt.isPresent()) {
            return Optional.empty();
        }

        Class<? extends Annotation> annotationType = opt.get();
        return Optional.of((ArgumentBinder<T, Message>) binders.stream()
            .filter(binder -> binder.getAnnotationType().equals(annotationType))
            .max(OrderUtil.COMPARATOR)
            .orElseThrow(() -> new IllegalArgumentException("Cannot bind argument " + argument.getName())));
    }
}
