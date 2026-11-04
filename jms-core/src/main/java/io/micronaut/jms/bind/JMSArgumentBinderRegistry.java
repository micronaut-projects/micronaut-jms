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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.bind.annotation.AnnotatedArgumentBinder;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.serdes.Deserializer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.Message;
import java.util.ArrayList;
import java.util.Collection;
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

    /**
     * @param conversionService the conversion service
     * @param beanContext       the bean context, to select the global {@link Deserializer}
     * @since 5.2.0
     */
    @Inject
    public JMSArgumentBinderRegistry(ConversionService conversionService, BeanContext beanContext) {
        this(conversionService, globalDeserializer(beanContext));
    }

    /**
     * @param conversionService the conversion service
     * @param deserializer      the deserializer of message bodies
     */
    public JMSArgumentBinderRegistry(ConversionService conversionService, Deserializer deserializer) {
        registerArgumentBinder(new DefaultBodyArgumentBinder(conversionService, deserializer));
        registerArgumentBinder(new MessageBodyHeaderArgumentBinder(conversionService, deserializer));
        registerArgumentBinder(new DefaultHeaderArgumentBinder(conversionService));
        registerArgumentBinder(new DefaultMessageArgumentBinder(conversionService));
    }

    /**
     * Selects the deserializer of listeners that don't name one: a {@code @Primary} bean, otherwise the bean with the
     * highest precedence, otherwise {@link DefaultSerializerDeserializer}, the {@code @DefaultImplementation} of
     * {@link Deserializer}. These are the rules an unqualified injection follows, but the default is recognised by its
     * type: Micronaut reads {@code @DefaultImplementation} from the metadata of one of the candidates, which beans
     * defined in other languages, such as Python, may not inherit from the interface. Named deserializers of
     * {@code @Queue} and {@code @Topic} are candidates too, and must not replace the default. A bean that
     * {@code @Replaces} the default, or {@link Deserializer} itself, which {@code @DefaultImplementation} makes a
     * replacement of the default, takes the default's place.
     *
     * @param beanContext the bean context
     * @return the global deserializer
     */
    private static Deserializer globalDeserializer(BeanContext beanContext) {
        Collection<BeanDefinition<Deserializer>> candidates = beanContext.getBeanDefinitions(Deserializer.class);
        List<BeanDefinition<Deserializer>> primary = candidates.stream().filter(BeanDefinition::isPrimary).toList();
        if (candidates.size() == 1 || primary.size() == 1) {
            return beanContext.getBean(candidates.size() == 1 ? candidates.iterator().next() : primary.get(0));
        }
        if (primary.isEmpty() && candidates.size() > 1) {
            List<BeanDefinition<Deserializer>> ordered = new ArrayList<>(candidates);
            ordered.sort(OrderUtil.ORDERED_COMPARATOR);
            if (ordered.get(0).getOrder() != ordered.get(1).getOrder()) {
                return beanContext.getBean(ordered.get(0));
            }
            for (BeanDefinition<Deserializer> candidate : ordered) {
                if (isDefault(candidate)) {
                    return beanContext.getBean(candidate);
                }
            }
        }
        // anything else, such as several primary beans, resolves and fails as an injection would
        return beanContext.getBean(Deserializer.class);
    }

    private static boolean isDefault(BeanDefinition<Deserializer> candidate) {
        return DefaultSerializerDeserializer.class.equals(candidate.getBeanType())
            || candidate.classValue(Replaces.class)
                .filter(replaced -> replaced == DefaultSerializerDeserializer.class || replaced == Deserializer.class)
                .isPresent();
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
