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
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.observation.JMSMessageObserver;
import io.micronaut.jms.observation.JMSObservation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Singleton;
import jakarta.jms.Message;
import org.jspecify.annotations.Nullable;

/**
 * Records the messages that JMS listeners process and that JMS producers send. Listener messages are counted as
 * {@value #LISTENER_MESSAGES} and timed as {@value #LISTENER_DURATION}, from the start of the listener method to the
 * end of the success or error handlers. Producer messages are counted as {@value #PRODUCER_MESSAGES} and timed as
 * {@value #PRODUCER_DURATION}, including the commit of a transacted send. The meters are tagged with the
 * {@code destination} and the {@code result}, {@code success} or {@code failure}, and failures with the
 * {@code exception} class. Disable them with {@code micronaut.metrics.binders.jms.enabled: false}.
 *
 * @since 5.2.0
 */
@Singleton
@Requires(classes = MeterRegistry.class)
@Requires(beans = MeterRegistry.class)
@Requires(property = JMSMetrics.ENABLED, notEquals = StringUtils.FALSE)
public final class JMSMetrics implements JMSMessageObserver {

    /**
     * The property that disables the metrics.
     */
    public static final String ENABLED = "micronaut.metrics.binders.jms.enabled";

    /**
     * The name of the counter of the messages that listeners process.
     */
    public static final String LISTENER_MESSAGES = "jms.listener.messages";

    /**
     * The name of the timer of the messages that listeners process.
     */
    public static final String LISTENER_DURATION = "jms.listener.duration";

    /**
     * The name of the counter of the messages that producers send.
     */
    public static final String PRODUCER_MESSAGES = "jms.producer.messages";

    /**
     * The name of the timer of the messages that producers send.
     */
    public static final String PRODUCER_DURATION = "jms.producer.duration";

    private final MeterRegistry meterRegistry;

    /**
     * @param meterRegistry The meter registry
     */
    JMSMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public JMSObservation process(JMSDestinationType destinationType, String destination, Message message) {
        return observe(LISTENER_MESSAGES, LISTENER_DURATION, destination);
    }

    @Override
    public JMSObservation send(JMSDestinationType destinationType, String destination, Message message) {
        return observe(PRODUCER_MESSAGES, PRODUCER_DURATION, destination);
    }

    @Override
    public int getOrder() {
        // completed last, so the duration includes the completion of the other observations
        return HIGHEST_PRECEDENCE;
    }

    private JMSObservation observe(String counter, String timer, String destination) {
        Timer.Sample sample = Timer.start(meterRegistry);
        return error -> {
            Tags tags = tags(destination, error);
            meterRegistry.counter(counter, tags).increment();
            sample.stop(meterRegistry.timer(timer, tags));
        };
    }

    private static Tags tags(String destination, @Nullable Throwable error) {
        Tags tags = Tags.of("destination", destination);
        return error == null
            ? tags.and("result", "success")
            : tags.and("result", "failure", "exception", error.getClass().getSimpleName());
    }
}
