/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.bind.annotation.Bindable;

import javax.jms.Message;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * An annotation that can be applied to method argument to indicate that the
 * argument is bound from the JMS TTL attribute on {@link javax.jms.MessageProducer#send(Message, int, int, long)}.
 *
 * @author jaecktec
 * @since 3.3.0
 */
@Documented
@Retention(RUNTIME)
@Target(PARAMETER)
@Bindable
public @interface MessageTTL {
}
