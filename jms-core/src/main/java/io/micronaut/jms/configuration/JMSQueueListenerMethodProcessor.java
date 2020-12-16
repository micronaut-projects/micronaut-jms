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
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.bind.JMSArgumentBinderRegistry;
import io.micronaut.jms.model.JMSDestinationType;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.micronaut.jms.model.JMSDestinationType.QUEUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Singleton
public class JMSQueueListenerMethodProcessor extends AbstractJMSListenerMethodProcessor<Queue> {

    private static final Pattern CONCURRENCY_PATTERN = Pattern.compile("([0-9]+)-([0-9]+)");

    public JMSQueueListenerMethodProcessor(BeanContext beanContext,
                                           JMSArgumentBinderRegistry jmsArgumentBinderRegistry) {
        super(beanContext, jmsArgumentBinderRegistry, Queue.class);
    }

    @Override
    protected ExecutorService getExecutorService(AnnotationValue<Queue> value) {
        final Optional<String> executorName = value.stringValue("executor");
        final Optional<String> concurrency = value.stringValue("concurrency");

        if (executorName.isPresent() && !executorName.get().isEmpty()) {
            return beanContext.findBean(ExecutorService.class, Qualifiers.byName(executorName.get()))
                .orElseThrow(() -> new IllegalStateException(
                    "There is no configured executor service for " + executorName.get()));
        }

        final Matcher matcher = CONCURRENCY_PATTERN.matcher(concurrency
            .orElseThrow(() -> new IllegalStateException(
                "If executor is not specified then concurrency must be specified")));
        if (!matcher.find() || matcher.groupCount() != 2) {
            throw new IllegalArgumentException("Concurrency must be of the form int-int (i.e. \"1-10\"). Concurrency provided was " + concurrency.get());
        }

        int numThreads = Integer.parseInt(matcher.group(1));
        int maxThreads = Integer.parseInt(matcher.group(2));
        return new ThreadPoolExecutor(
            numThreads,
            maxThreads,
            500L,
            MILLISECONDS,
            new LinkedBlockingQueue<>(numThreads),
            Executors.defaultThreadFactory());
    }

    @Override
    protected JMSDestinationType getDestinationType() {
        return QUEUE;
    }
}
