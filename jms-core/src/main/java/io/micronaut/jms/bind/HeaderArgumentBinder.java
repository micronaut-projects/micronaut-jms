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
import io.micronaut.jms.model.JMSHeaders;
import io.micronaut.messaging.annotation.Header;

import javax.jms.Message;
import java.util.Optional;

/***
 * Argument binder for binding headers from a {@link Message} to a method argument annotated with {@link Header}.
 *
 * @author elliott
 * @since 1.0
 */
public class HeaderArgumentBinder extends AbstractChainedArgumentBinder {

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Message source) {
        if (context.isAnnotationPresent(Header.class)) {
            final String headerName = context.getAnnotation(Header.class).stringValue()
                    .orElseThrow(() -> new IllegalStateException("@Header must specify a headerName."));
            return () -> Optional.ofNullable(JMSHeaders.getHeader(headerName, source, context.getArgument().getType()));
        }
        return Optional::empty;
    }

    @Override
    public boolean canBind(Argument<?> argument) {
        return argument.isAnnotationPresent(Header.class);
    }
}
