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
package io.micronaut.jms.tracing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.observation.JMSMessageObserver;
import io.micronaut.jms.observation.JMSObservation;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.inject.Singleton;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Traces the messages that JMS listeners process and that JMS producers send with OpenTelemetry, for example with
 * the OpenTelemetry bean of Micronaut Tracing. A producer span ({@code send <destination>}) propagates its context in
 * message properties, and a consumer span ({@code process <destination>}) continues it and is the current span while
 * the listener method runs. Property names that are not Java identifiers, such as those of B3 headers, have their
 * dashes replaced with {@value #DASH}, as the OpenTelemetry Java agent does. Disable it with
 * {@code micronaut.jms.tracing.enabled: false}.
 *
 * @since 5.2.0
 */
@Singleton
@Requires(classes = OpenTelemetry.class)
@Requires(beans = OpenTelemetry.class)
@Requires(property = JMSTracing.ENABLED, notEquals = StringUtils.FALSE)
public final class JMSTracing implements JMSMessageObserver {

    /**
     * The property that disables tracing.
     */
    public static final String ENABLED = "micronaut.jms.tracing.enabled";

    /**
     * The name of the instrumentation scope of the spans.
     */
    public static final String INSTRUMENTATION_SCOPE = "io.micronaut.jms";

    /**
     * The replacement of dashes in the names of message properties.
     */
    public static final String DASH = "__dash__";

    private static final Logger LOGGER = LoggerFactory.getLogger(JMSTracing.class);

    private static final AttributeKey<String> MESSAGING_SYSTEM = AttributeKey.stringKey("messaging.system");
    private static final AttributeKey<String> DESTINATION_NAME = AttributeKey.stringKey("messaging.destination.name");
    private static final AttributeKey<String> OPERATION_NAME = AttributeKey.stringKey("messaging.operation.name");
    private static final AttributeKey<String> OPERATION_TYPE = AttributeKey.stringKey("messaging.operation.type");
    private static final AttributeKey<String> MESSAGE_ID = AttributeKey.stringKey("messaging.message.id");
    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

    private static final TextMapSetter<Message> SETTER = (message, key, value) -> {
        if (message != null) {
            try {
                message.setStringProperty(key.replace("-", DASH), value);
            } catch (JMSException | RuntimeException e) {
                // for example a received message that is sent again, whose properties are read-only
                LOGGER.debug("Unable to propagate the trace context in property {}: {}", key, e.getMessage());
            }
        }
    };

    private static final TextMapGetter<Message> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Message message) {
            try {
                List<String> keys = new ArrayList<>();
                for (Object name : Collections.list(message.getPropertyNames())) {
                    keys.add(name.toString().replace(DASH, "-"));
                }
                return keys;
            } catch (JMSException | RuntimeException _) {
                return List.of();
            }
        }

        @Override
        public @Nullable String get(@Nullable Message message, String key) {
            if (message == null) {
                return null;
            }
            try {
                return message.getStringProperty(key.replace("-", DASH));
            } catch (JMSException | RuntimeException _) {
                return null;
            }
        }
    };

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    /**
     * @param openTelemetry The OpenTelemetry instance
     */
    JMSTracing(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
    }

    @Override
    public JMSObservation process(JMSDestinationType destinationType, String destination, Message message) {
        Context parent = propagator.extract(Context.current(), message, GETTER);
        Span span = spanBuilder("process", destination, SpanKind.CONSUMER)
            .setParent(parent)
            .startSpan();
        setMessageId(span, message);
        Scope scope = span.makeCurrent();
        return error -> {
            try {
                end(span, error);
            } finally {
                scope.close();
            }
        };
    }

    @Override
    public JMSObservation send(JMSDestinationType destinationType, String destination, Message message) {
        Span span = spanBuilder("send", destination, SpanKind.PRODUCER).startSpan();
        propagator.inject(Context.current().with(span), message, SETTER);
        return error -> {
            // the provider assigns the message ID when it sends the message
            setMessageId(span, message);
            end(span, error);
        };
    }

    private SpanBuilder spanBuilder(String operation, String destination, SpanKind kind) {
        return tracer.spanBuilder(operation + " " + destination)
            .setSpanKind(kind)
            .setAttribute(MESSAGING_SYSTEM, "jms")
            .setAttribute(DESTINATION_NAME, destination)
            .setAttribute(OPERATION_NAME, operation)
            .setAttribute(OPERATION_TYPE, operation);
    }

    private static void setMessageId(Span span, Message message) {
        try {
            String id = message.getJMSMessageID();
            if (id != null) {
                span.setAttribute(MESSAGE_ID, id);
            }
        } catch (JMSException | RuntimeException _) {
            // the message ID is optional
        }
    }

    private static void end(Span span, @Nullable Throwable error) {
        if (error != null) {
            span.recordException(error);
            span.setStatus(StatusCode.ERROR);
            span.setAttribute(ERROR_TYPE, error.getClass().getName());
        }
        span.end();
    }
}
