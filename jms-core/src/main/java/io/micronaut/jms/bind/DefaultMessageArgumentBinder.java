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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.jms.annotations.Message;

import java.util.Optional;

/**
 * Binds a {@link javax.jms.Message} to a method argument annotated with {@link Message}.
 *
 * @author Burt Beckwith
 * @since 1.0.0
 */
public class DefaultMessageArgumentBinder extends AbstractJmsArgumentBinder<Message> {

    /**
     * Constructor.
     *
     * @param conversionService conversionService
     */
    public DefaultMessageArgumentBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context,
                                      javax.jms.Message source) {
        return () -> Optional.of(source);
    }

    @Override
    public Class<Message> getAnnotationType() {
        return Message.class;
    }
}
