/*
 * Copyright 2017-2021 original authors
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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.jms.serdes.Deserializer;
import io.micronaut.messaging.annotation.MessageBody;

import jakarta.jms.Message;
import java.util.Optional;

/**
 * Binds the {@link MessageBody} annotation.
 *
 * @author Denis Stepanov
 * @since 1.0.0
 */
public class MessageBodyHeaderArgumentBinder extends AbstractJmsArgumentBinder<MessageBody> {

    private final Deserializer deserializer;

    /**
     * Constructor.
     *
     * @param conversionService conversionService
     * @param deserializer      deserializer
     */
    public MessageBodyHeaderArgumentBinder(ConversionService conversionService, Deserializer deserializer) {
        super(conversionService);
        this.deserializer = deserializer;
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Message source) {
        return () -> Optional.of(deserializer.deserialize(source, context.getArgument().getType()));
    }

    @Override
    public Class<MessageBody> getAnnotationType() {
        return MessageBody.class;
    }
}

