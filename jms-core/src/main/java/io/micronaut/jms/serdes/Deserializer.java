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

/**
 * Extracts a {@link Message} body to an instance of the specified type.
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
public interface Deserializer {

    /**
     * Extract the body of the message into a sensible type.
     *
     * @param message the message
     * @return the extracted message body as an instance of a sensible type
     */
    default Object deserialize(Message message) {
        return deserialize(message, Object.class);
    }

    /**
     * Extract the body of the message into the specified type.
     *
     * @param message the message
     * @param clazz the type
     * @param <T> the type
     * @return the extracted message body as an instance of the specified type
     */
    <T> T deserialize(Message message, Class<T> clazz);
}
