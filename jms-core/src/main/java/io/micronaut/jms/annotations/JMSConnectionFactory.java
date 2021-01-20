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
import io.micronaut.context.annotation.DefaultScope;

import javax.inject.Named;
import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Denotes a JMS Connection Factory bean. Any connection factory that a
 * {@link JMSListener} references must be annotated with this annotation. If a
 * connection factory is present in the Bean Context but not annotated with
 * this annotation, the post-processing logic will fail and the
 * {@link JMSListener} will not work.
 * <p>
 * Usage:
 * <pre>
 * &#64;JMSConnectionFactory("myConnectionFactory")
 * public ConnectionFactory myConnectionFactory() {
 *     return new ActiveMqConnectionFactory("vm://localhost?broker.persist=false");
 * }
 *  </pre>
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD, TYPE})
@Bean
@DefaultScope(Singleton.class)
public @interface JMSConnectionFactory {

    /**
     * Name to identify the {@link javax.jms.ConnectionFactory} bean in the
     * context. This is used by the {@link io.micronaut.jms.configuration.AbstractJMSListenerMethodProcessor}
     * and the {@link JMSListener} to identify which {@link javax.jms.ConnectionFactory} to use.
     *
     * @return the name of the bean.
     * @see JMSListener
     * @see io.micronaut.jms.configuration.AbstractJMSListenerMethodProcessor
     */
    @AliasFor(annotation = Named.class, member = "value")
    String value();
}
