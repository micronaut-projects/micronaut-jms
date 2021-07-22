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
package io.micronaut.jms.util;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.function.Supplier;

/**
 * Assertion utility methods.
 *
 * @author Burt Beckwith
 * @since 1.0.0
 */
public abstract class Assert {

    /**
     * Throws an IllegalArgumentException if the object is null.
     *
     * @param object  the object
     * @param message the exception message to use if null
     * @throws IllegalArgumentException if the object is null
     */
    public static void notNull(@Nullable Object object,
                               String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Throws an IllegalArgumentException if the object is null.
     *
     * @param object  the object
     * @param message the exception message to use if null
     * @throws IllegalArgumentException if the object is null
     */
    public static void notNull(@Nullable Object object,
                               @NonNull Supplier<String> message) {
        if (object == null) {
            throw new IllegalArgumentException(message.get());
        }
    }

    /**
     * Throws an IllegalArgumentException if the boolean expression is false.
     *
     * @param expression a boolean expression
     * @param message the exception message to use if false
     * @throws IllegalArgumentException if the expression is false
     */
    public static void isTrue(boolean expression,
                              @NonNull Supplier<String> message) {
        if (!expression) {
            throw new IllegalArgumentException(message.get());
        }
    }
}
