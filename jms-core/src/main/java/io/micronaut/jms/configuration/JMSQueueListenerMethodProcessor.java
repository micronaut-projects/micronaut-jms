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
import io.micronaut.jms.util.Assert;

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

/**
 * Registers a {@link io.micronaut.jms.listener.JMSListener} for
 * methods annotated with {@link Queue}.
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
@Singleton
public class JMSQueueListenerMethodProcessor extends AbstractJMSListenerMethodProcessor<Queue> {

    private static final Pattern CONCURRENCY_PATTERN = Pattern.compile("([0-9]+)-([0-9]+)");
    private static final long DEFAULT_KEEP_ALIVE_TIME = 500; // TODO BB

    public JMSQueueListenerMethodProcessor(BeanContext beanContext,
                                           JMSArgumentBinderRegistry registry) {
        super(beanContext, registry, Queue.class);
    }

    @Override
    protected ExecutorService getExecutorService(AnnotationValue<Queue> value) {
        final Optional<String> executorName = value.stringValue("executor");
        final Optional<String> concurrency = value.stringValue("concurrency");

        if (executorName.isPresent() && !executorName.get().isEmpty()) {
            return beanContext.findBean(ExecutorService.class, Qualifiers.byName(executorName.get()))
                .orElseThrow(() -> new IllegalStateException(
                    "No ExecutorService bean found with name " + executorName.get()));
        }

        final Matcher matcher = CONCURRENCY_PATTERN.matcher(concurrency
            .orElseThrow(() -> new IllegalStateException(
                "Concurrency must be specified if ExecutorService is not specified")));
        Assert.isTrue(matcher.find() && matcher.groupCount() == 2,
            () -> "Concurrency must be of the form int-int (e.g. \"1-10\"). " +
                "Concurrency provided was " + concurrency.get());

        int numThreads = Integer.parseInt(matcher.group(1));
        int maxThreads = Integer.parseInt(matcher.group(2));
        return new ThreadPoolExecutor(
            numThreads,
            maxThreads,
            DEFAULT_KEEP_ALIVE_TIME,
            MILLISECONDS,
            new LinkedBlockingQueue<>(numThreads),
            Executors.defaultThreadFactory());
    }

    @Override
    protected JMSDestinationType getDestinationType() {
        return QUEUE;
    }
}
