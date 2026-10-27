/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.jms.metrics;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jms.listener.GlobalJMSListenerErrorHandler;
import io.micronaut.jms.listener.GlobalJMSListenerSuccessHandler;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Singleton;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.Topic;

/**
 * Counts the messages that JMS listeners handle, as the {@value #MESSAGES} counter, tagged with the
 * {@code destination} and the {@code result}, {@code success} or {@code failure}. Failures are also tagged with
 * the {@code exception} class. Disable it with {@code micronaut.metrics.binders.jms.enabled: false}.
 *
 * @since 5.2.0
 */
@Singleton
@Requires(classes = MeterRegistry.class)
@Requires(beans = MeterRegistry.class)
@Requires(property = JMSListenerMetrics.ENABLED, notEquals = StringUtils.FALSE)
public final class JMSListenerMetrics implements GlobalJMSListenerSuccessHandler, GlobalJMSListenerErrorHandler {

    /**
     * The property that disables the metrics.
     */
    public static final String ENABLED = "micronaut.metrics.binders.jms.enabled";

    /**
     * The name of the counter of handled messages.
     */
    public static final String MESSAGES = "jms.listener.messages";

    private static final String DESTINATION = "destination";
    private static final String RESULT = "result";

    private final MeterRegistry meterRegistry;

    /**
     * @param meterRegistry The meter registry
     */
    JMSListenerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void handle(Session session, Message message) {
        meterRegistry.counter(MESSAGES, DESTINATION, destination(message), RESULT, "success").increment();
    }

    @Override
    public void handle(Session session, Message message, Throwable ex) {
        meterRegistry.counter(MESSAGES, DESTINATION, destination(message), RESULT, "failure",
            "exception", ex.getClass().getSimpleName()).increment();
    }

    @Override
    public int getOrder() {
        return DEFAULT_POSITION;
    }

    private static String destination(Message message) {
        try {
            Destination destination = message.getJMSDestination();
            if (destination instanceof Queue queue) {
                return queue.getQueueName();
            }
            if (destination instanceof Topic topic) {
                return topic.getTopicName();
            }
            return destination == null ? "unknown" : destination.toString();
        } catch (JMSException _) {
            return "unknown";
        }
    }
}
