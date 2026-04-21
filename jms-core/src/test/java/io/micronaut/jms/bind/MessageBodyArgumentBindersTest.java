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
package io.micronaut.jms.bind;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.jms.serdes.Deserializer;
import jakarta.jms.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MessageBodyArgumentBindersTest {

    @Test
    void defaultBodyBinderPreservesArgumentTypeVariables() {
        CapturingDeserializer deserializer = new CapturingDeserializer();
        assertBinderPreservesArgumentTypeVariables(new DefaultBodyArgumentBinder(ConversionService.SHARED, deserializer), deserializer);
    }

    @Test
    void messageBodyHeaderBinderPreservesArgumentTypeVariables() {
        CapturingDeserializer deserializer = new CapturingDeserializer();
        assertBinderPreservesArgumentTypeVariables(new MessageBodyHeaderArgumentBinder(ConversionService.SHARED, deserializer), deserializer);
    }

    @SuppressWarnings("unchecked")
    private static void assertBinderPreservesArgumentTypeVariables(AbstractJmsArgumentBinder<?> binder,
                                                                   CapturingDeserializer deserializer) {
        Argument<List<String>> argument = Argument.listOf(String.class);
        ArgumentConversionContext<Object> context = ConversionContext.of((Argument<Object>) (Argument<?>) argument);
        Object bound = binder.bind(context, message()).get();

        assertSame(deserializer.value, bound);
        assertSame(argument, deserializer.capturedArgument.get());
        assertEquals(String.class, deserializer.capturedArgument.get().getFirstTypeVariable().orElseThrow().getType());
    }

    private static Message message() {
        return (Message) Proxy.newProxyInstance(
            Message.class.getClassLoader(),
            new Class<?>[]{Message.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "toString" -> "MessageBodyArgumentBindersTestMessage";
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> null;
            }
        );
    }

    private static final class CapturingDeserializer implements Deserializer {

        private final AtomicReference<Argument<?>> capturedArgument = new AtomicReference<>();
        private final List<String> value = List.of("value");

        @Override
        public <T> T deserialize(Message message, Argument<T> argument) {
            capturedArgument.set(argument);
            return (T) value;
        }

        @Override
        public <T> T deserialize(Message message, Class<T> clazz) {
            throw new UnsupportedOperationException("Class-based deserialization should not be used");
        }
    }
}
