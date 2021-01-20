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
import io.micronaut.jms.model.JMSHeaders;
import io.micronaut.messaging.annotation.Header;

import javax.jms.Message;
import java.util.Optional;

/**
 * Binds headers from a {@link Message} to a method argument annotated with
 * {@link Header}.
 *
 * @author Elliott Pope
 * @since 1.0.0
 */
public class DefaultHeaderArgumentBinder extends AbstractJmsArgumentBinder<Header> {

    /**
     * Constructor.
     *
     * @param conversionService conversionService
     */
    public DefaultHeaderArgumentBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context,
                                      Message source) {

        final String headerName = context.getAnnotationMetadata().stringValue(Header.class)
            .orElseThrow(() -> new IllegalStateException("@Header must specify a headerName."));

        return () -> Optional.ofNullable(
            JMSHeaders.getHeader(headerName, source, context.getArgument().getType()));
    }

    @Override
    public Class<Header> getAnnotationType() {
        return Header.class;
    }
}
