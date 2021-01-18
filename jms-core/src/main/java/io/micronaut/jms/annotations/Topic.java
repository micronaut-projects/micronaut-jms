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

import io.micronaut.context.annotation.Executable;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static javax.jms.Session.AUTO_ACKNOWLEDGE;

/**
 * Annotation to bind a {@link javax.jms.Topic} to a method for receiving or
 * sending a {@link javax.jms.Message}.
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
public @interface Topic {

    /**
     * The name of the topic to target.
     * @return the name
     */
    String value();

    /**
     * The name of an {@link java.util.concurrent.ExecutorService} in the bean
     * context to execute tasks on when receiving a {@link javax.jms.Message}
     * as part of a {@link JMSListener}. The executor can be maintained by
     * Micronaut using the {@link io.micronaut.scheduling.executor.UserExecutorConfiguration}.
     *
     * @return the executor service name
     */
    String executor() default "";

    /**
     * The name of a {@link io.micronaut.jms.serdes.Serializer} in the bean
     * context to use to serialize an object into a {@link javax.jms.Message}
     * when sending. If not specified, defaults to
     * {@link io.micronaut.jms.serdes.DefaultSerializerDeserializer}.
     *
     * @return the serializer bean name
     */
    String serializer() default "";

    /**
     * @return the acknowledge mode for the {@link io.micronaut.jms.listener.JMSListenerContainer}.
     * @see javax.jms.Session
     */
    int acknowledgeMode() default AUTO_ACKNOWLEDGE;

    /**
     * Whether message receipt is transacted. The broker must support
     * transacted sessions.
     *
     * @return true if transacted
     * @see javax.jms.Session
     */
    boolean transacted() default false;
}
