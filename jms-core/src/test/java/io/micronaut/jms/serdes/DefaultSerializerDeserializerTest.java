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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultSerializerDeserializerTest {

    @Test
    void deserializeTextPreservesListElementType() {
        try (ApplicationContext context = ApplicationContext.run()) {
            DefaultSerializerDeserializer deserializer = context.getBean(DefaultSerializerDeserializer.class);

            List<String> values = deserializer.deserialize(textMessage("[\"alpha\",\"beta\"]"), Argument.listOf(String.class));

            assertEquals(List.of("alpha", "beta"), values);
        }
    }

    private static TextMessage textMessage(String text) {
        return (TextMessage) Proxy.newProxyInstance(
            TextMessage.class.getClassLoader(),
            new Class<?>[]{TextMessage.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getText" -> text;
                case "toString" -> "TextMessage[" + text + "]";
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> null;
            }
        );
    }
}
