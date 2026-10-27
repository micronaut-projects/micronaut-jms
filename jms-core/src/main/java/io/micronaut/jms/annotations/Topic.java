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
package io.micronaut.jms.annotations;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Executable;
import io.micronaut.jms.listener.JMSListenerErrorHandler;
import io.micronaut.jms.listener.JMSListenerSuccessHandler;
import io.micronaut.jms.model.JMSDeliveryMode;
import io.micronaut.messaging.annotation.MessageMapping;

import jakarta.jms.Message;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static jakarta.jms.Session.AUTO_ACKNOWLEDGE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation to bind a {@link jakarta.jms.Topic} to a method for receiving or
 * sending a {@link jakarta.jms.Message}.
 * <p>
 * Usage:
 * <pre>
 * &#64;JMSListener("myConnectionFactory")
 * public class Listener {
 *      &#64;Topic("my-topic")
 *      public &lt;T&gt; void handle(@Body T body, @Header(JMSHeaders.JMS_MESSAGE_ID) String messageID) {
 *          // do some logic with body and messageID
 *      }
 *
 *      &#64;Topic("my-topic-2")
 *      public &lt;T&gt; void handle(@Body T body, @Header("X-Arbitrary-Header") String arbitraryHeader) {
 *          // do some logic with body and arbitraryHeader
 *      }
 * }
 * </pre>
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
@Executable(processOnStartup = true)
@MessageMapping
public @interface Topic {

    /**
     * The name of the topic to target.
     * @return the name
     */
    @AliasFor(annotation = MessageMapping.class, member = "value")
    String value();

    /**
     * The name of a {@link io.micronaut.jms.serdes.Serializer} in the bean
     * context to use to serialize an object into a {@link jakarta.jms.Message}
     * when sending. If not specified, defaults to
     * {@link io.micronaut.jms.serdes.DefaultSerializerDeserializer}.
     *
     * @return the serializer bean name
     */
    String serializer() default "";

    /**
     * @return the acknowledge mode for the {@link io.micronaut.jms.listener.JMSListener}.
     * @see jakarta.jms.Session
     */
    int acknowledgeMode() default AUTO_ACKNOWLEDGE;

    /**
     * Whether message receipt is transacted. The broker must support
     * transacted sessions.
     *
     * @return true if transacted
     * @see jakarta.jms.Session
     */
    boolean transacted() default false;

    /**
     * @return the message selector for the topic
     */
    String messageSelector() default "";

    /**
     * The number of consumers of the listener method, each with its own session, so that messages are handled
     * concurrently. More than one consumer requires a {@link #shared()} subscription: the consumers of any other
     * topic listener would each receive every message. Ignored by producers.
     *
     * @return the number of consumers
     * @since 5.2.0
     */
    int consumers() default 1;

    /**
     * The delivery mode of the messages that a producer method sends. Ignored by listeners. A
     * {@link io.micronaut.jms.annotations.MessageDeliveryMode} argument overrides it.
     *
     * @return the delivery mode
     * @since 5.2.0
     */
    JMSDeliveryMode deliveryMode() default JMSDeliveryMode.PERSISTENT;

    /**
     * The priority, from 0 (lowest) to 9, of the messages that a producer method sends. Ignored by listeners. A
     * {@link io.micronaut.jms.annotations.MessagePriority} argument overrides it.
     *
     * @return the priority
     * @since 5.2.0
     */
    int priority() default Message.DEFAULT_PRIORITY;

    /**
     * The name of the subscription. Required when {@link #durable()}, {@link #shared()} or {@link #clientId()}
     * is set, and requires {@link #durable()} or {@link #shared()} in turn; ignored on producers.
     *
     * @return the subscription name
     * @since 5.2.0
     */
    String subscription() default "";

    /**
     * Whether the subscription is durable, so messages published while the listener is not connected are
     * kept for it. An unshared durable subscription gets a connection of its own, with the
     * {@link #clientId()} as its client ID.
     *
     * @return whether the subscription is durable
     * @since 5.2.0
     */
    boolean durable() default false;

    /**
     * Whether the subscription is shared, so its messages are spread over every listener with the same
     * subscription name. Requires a JMS 2.0 provider.
     *
     * @return whether the subscription is shared
     * @since 5.2.0
     */
    boolean shared() default false;

    /**
     * The client ID of the listener's connection. Defaults to the {@link #subscription()} name for an
     * unshared durable subscription. When set, the listener gets a connection of its own.
     *
     * @return the client ID
     * @since 5.2.0
     */
    String clientId() default "";

    /**
     * The name of a {@link io.micronaut.jms.serdes.Deserializer} in the bean
     * context that a listener method uses to convert each {@link jakarta.jms.Message} of the topic
     * into its {@code @MessageBody} argument. If not specified, defaults to
     * {@link io.micronaut.jms.serdes.DefaultSerializerDeserializer}.
     *
     * @return the deserializer bean name
     * @since 5.2.0
     */
    String deserializer() default "";

    /**
     * The success handlers to be injected into the message handling logic.
     * @return the classes of the success handlers to be added. These handlers must be present as {@link jakarta.inject.Singleton}
     *  instances.
     *
     *  @since 3.0.0
     */
    Class<? extends JMSListenerSuccessHandler>[] successHandlers() default {};

    /**
     * The error handlers to be injected into the message handling logic.
     * @return the classes of the error handlers to be added. These handlers must be present as {@link jakarta.inject.Singleton}
     *  instances.
     *
     *  @since 3.0.0
     */
    Class<? extends JMSListenerErrorHandler>[] errorHandlers() default {};
}
