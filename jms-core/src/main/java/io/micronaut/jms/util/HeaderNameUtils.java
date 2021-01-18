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

/**
 * JMS header names must be valid Java identifiers. These methods escape and
 * un-escape non-JMS header names.
 *
 * @author Burt Beckwith
 * @since 1.0.0
 */
public class HeaderNameUtils {

    private static final String DOT = "\\.";
    private static final String DOT_ENCODED = "_DOT_";
    private static final String HYPHEN = "-";
    private static final String HYPHEN_ENCODED = "_HYPHEN_";

    public static String encode(String key) {
        return (key == null)
            ? null
            : key.replaceAll(DOT, DOT_ENCODED).replaceAll(HYPHEN, HYPHEN_ENCODED);
    }

    public static String decode(String key) {
        return (key == null)
            ? null
            : key.replaceAll(DOT_ENCODED, DOT).replaceAll(HYPHEN_ENCODED, HYPHEN);
    }
}
