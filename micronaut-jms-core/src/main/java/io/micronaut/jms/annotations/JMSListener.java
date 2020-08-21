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
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.DefaultScope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/***
 *
 * Annotation for declaring a class for post-processing. Any class annotated with {@link JMSListener} must contain at
 *  least one method annotated with {@link Queue} or {@link Topic} or else an {@link IllegalStateException} will be thrown.
 *
 * Additionally, the value specified by the {@link JMSListener} must correspond to exactly one {@link JMSConnectionFactory}
 *  or else an {@link IllegalStateException} will be thrown.
 *
 * Usage:
 * <pre>
 *     {@code
 * @JMSConnectionFactory("connectionFactoryOne")
 * public ConnectionFactory connectionFactoryOne() {
 *     return new ActiveMqConnectionFactory("vm://localhost?broker.persist=false");
 * }
 *
 *
 * @JMSConnectionFactory("connectionFactoryTwo")
 * public ConnectionFactory connectionFactoryTwo() {
 *     RMQConnectionFactory connectionFactory = new RMQConnectionFactory();
 *     connectionFactory.setUsername("guest");
 *     connectionFactory.setPassword("guest");
 *     connectionFactory.setVirtualHost("/");
 *     connectionFactory.setHost("localhost");
 *     connectionFactory.setPort(5672);
 *     return connectionFactory;
 * }
 *
 * @JMSListener("connectionFactoryOne")
 * public static class ListenerOne {
 *      @Queue("my-activemq-queue")
 *      public void onMessage(String message) {
 *          // do logic
 *      }
 * }
 *
 * @JMSListener("connectionFactoryTwo")
 * public static class ListenerTwo {
 *      @Queue("my-rabbitmq-queue")
 *      public void handle(Integer message) {
 *          // do logic
 *      }
 * }
 *     }
 * </pre>
 *
 * @author elliott
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Bean
@DefaultScope(Context.class)
public @interface JMSListener {

    /***
     * Name of the {@link javax.jms.ConnectionFactory} bean in the context to use to set up the
     *      {@link io.micronaut.jms.listener.JMSListenerContainer}. The name must correspond to a bean
     *      annotated with {@link JMSConnectionFactory} and the values must be the same.
     *
     * @return the name of the {@link JMSConnectionFactory} to use.
     */
    @AliasFor(member = "connectionFactory")
    String value() default "";

    /***
     * Name of the {@link javax.jms.ConnectionFactory} bean in the context to use to set up the
     *      {@link io.micronaut.jms.listener.JMSListenerContainer}. The name must correspond to a bean
     *      annotated with {@link JMSConnectionFactory} and the values must be the same.
     *
     * @return the name of the {@link JMSConnectionFactory} to use.
     */
    @AliasFor(member = "value")
    String connectionFactory() default "";
}
