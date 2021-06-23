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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.bind.JMSArgumentBinderRegistry;
import io.micronaut.jms.model.JMSDestinationType;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.micronaut.jms.model.JMSDestinationType.TOPIC;

/**
 * Registers a {@link io.micronaut.jms.listener.JMSListener} for
 * methods annotated with {@link Topic}.
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
@Singleton
public class JMSTopicListenerMethodProcessor extends AbstractJMSListenerMethodProcessor<Topic> {

    public JMSTopicListenerMethodProcessor(BeanContext beanContext,
                                           JMSArgumentBinderRegistry registry) {
        super(beanContext, registry, Topic.class);
    }

    @Override
    protected ExecutorService getExecutorService(AnnotationValue<Topic> value) {

        final Optional<String> executorName = value.stringValue("executor");
        if (executorName.isPresent() && !executorName.get().isEmpty()) {
            return beanContext.findBean(ExecutorService.class, Qualifiers.byName(executorName.get()))
                .orElseThrow(() -> new IllegalStateException(
                    "No ExecutorService bean found with name " + executorName.get()));
        }

        return Executors.newSingleThreadExecutor();
    }

    @Override
    protected JMSDestinationType getDestinationType() {
        return TOPIC;
    }
}
