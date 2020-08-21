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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/***
 *
 * Annotation required to bind a {@link javax.jms.Topic} to a method for receiving or sending a {@link javax.jms.Message}.
 *
 * Usage:
 * <pre>
 *      {@code
 * @JMSListener("myConnectionFactory")
 * public class Listener {
 *      @Topic("my-queue")
 *      public <T> void handle(@Body T body, @Header(JMSHeaders.JMS_MESSAGE_ID) String messageID) {
 *          // do some logic with body and messageID
 *      }
 *
 *      @Topic("my-queue-2")
 *      public <T> void handle(@Body T body, @Header("X-Arbitrary-Header") String arbitraryHeader) {
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
public @interface Topic {

    /***
     * @return the name of the topic to target.
     */
    @AliasFor(member = "destination")
    String value() default "";

    /***
     * @return the name of the topic to target.
     */
    @AliasFor(member = "value")
    String destination() default "";
}
