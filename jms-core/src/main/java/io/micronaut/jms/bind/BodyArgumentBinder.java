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
import io.micronaut.core.type.Argument;
import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.serdes.Deserializer;
import io.micronaut.messaging.annotation.Body;

import javax.jms.Message;
import java.util.Optional;

/***
 * Argument binder for binding a {@link Message} to a method argument annotated with {@link Body}.
 *
 * @author elliott
 * @since 1.0
 */
public class BodyArgumentBinder extends AbstractChainedArgumentBinder {

    private final Deserializer deserializer = new DefaultSerializerDeserializer();

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context,
                                      Message source) {
        return () -> Optional.of(deserializer.deserialize(source, context.getArgument().getType()));
    }

    @Override
    public boolean canBind(Argument<?> argument) {
        return argument.isDeclaredAnnotationPresent(Body.class);
    }
}
