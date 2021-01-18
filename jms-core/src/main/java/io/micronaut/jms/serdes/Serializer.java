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
package io.micronaut.jms.serdes;

import javax.jms.Message;
import javax.jms.Session;

/**
 * Serializes an object into a {@link Message}.
 *
 * @param <T> the object type
 * @author Elliott Pope
 * @since 1.0.0
 */
@FunctionalInterface
public interface Serializer<T> {

    /**
     * Create a message from the body.
     *
     * @param session the JMS session
     * @param body the message body
     * @return the message
     */
    Message serialize(Session session, T body);
}
