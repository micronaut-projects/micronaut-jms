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
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.serdes.Deserializer;

import javax.jms.Session;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/***
 *
 * Annotation required to bind a {@link javax.jms.Queue} to a method for receiving or sending a {@link javax.jms.Message}.
 *
 * Usage:
 * <pre>
 *      {@code
 * @JMSListener("myConnectionFactory")
 * public class Listener {
 *      @Queue(
 *          destination = "my-queue",
 *          executor = "micronaut-executor-service"
 *      )
 *      public <T> void handle(T body, @Header(JMSHeaders.JMS_MESSAGE_ID) String messageID) {
 *          // do some logic with body and messageID
 *      }
 *
 *      @Queue(
 *          destination = "my-queue-2",
 *          concurrency = "1-5",
 *          transacted = true,
 *          acknowledgement = Session.CLIENT_ACKNOWLEDGE
 *      )
 *      public <T> void handle(T body, @Header("X-Arbitrary-Header") String arbitraryHeader) {
 *          // do some logic with body and arbitraryHeader
 *      }
 * }
 *      }
 * </pre>
 *
 * @author elliott
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Executable(processOnStartup = true)
public @interface Queue {

    /***
     * @return the name of the queue to target.
     */
    @AliasFor(member = "destination")
    String value() default "";

    /***
     * @return the name of the queue to target.
     */
    @AliasFor(member = "value")
    String destination() default "";

    /***
     * @return the {@link Deserializer} to use to deserialize {@link javax.jms.Message} into an {@link Object}
     */
    Class<? extends Deserializer> deserializer() default DefaultSerializerDeserializer.class;

    /***
     * @return the size of the thread pool to use when used in conjunction with {@link JMSListener}. The value
     *      should be of the form x-y where x is the initial size of the thread pool and y is the maximum size of
     *      the thread pool. If this option is specified, then a new thread pool will be created and destroyed with
     *      the {@link io.micronaut.jms.listener.JMSListenerContainer}. This option cannot be used in conjunction
     *      with {@link Queue#executor()} and if both are specified then the {@link Queue#executor()} value will be used.
     */
    String concurrency() default "1-1";

    /***
     * @return the name of an {@link java.util.concurrent.ExecutorService} in the bean context to execute tasks on
     *      receiving a {@link javax.jms.Message} as part of a {@link JMSListener}. The executor can be maintained by
     *      Micronaut using the {@link io.micronaut.scheduling.executor.UserExecutorConfiguration}.
     */
    String executor() default "";

    /***
     * @return the acknowledgement mode for the {@link io.micronaut.jms.listener.JMSListenerContainer}.
     *
     * @see Session
     */
    int acknowledgement() default Session.AUTO_ACKNOWLEDGE;

    /***
     * @return true if the message receipt is transacted, false otherwise. The broker must support transacted
     *      sessions
     *
     *
     * @see Session
     */
    boolean transacted() default false;
}
