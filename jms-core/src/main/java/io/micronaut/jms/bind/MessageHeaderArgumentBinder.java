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
import io.micronaut.messaging.annotation.MessageHeader;

import javax.jms.Message;

/**
 * Binds the {@link MessageHeader} annotation.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MessageHeaderArgumentBinder extends AbstractJmsArgumentBinder<MessageHeader> {
    private final DefaultHeaderArgumentBinder defaultHeaderArgumentBinder;

    public MessageHeaderArgumentBinder(ConversionService<?> conversionService, DefaultHeaderArgumentBinder defaultHeaderArgumentBinder) {
        super(conversionService);
        this.defaultHeaderArgumentBinder = defaultHeaderArgumentBinder;
    }

    @Override
    public Class<MessageHeader> getAnnotationType() {
        return MessageHeader.class;
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Message source) {
        return defaultHeaderArgumentBinder.bind(context, source);
    }
}
