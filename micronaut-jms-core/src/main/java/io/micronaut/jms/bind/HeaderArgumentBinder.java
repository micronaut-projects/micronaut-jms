package io.micronaut.jms.bind;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.jms.model.JMSHeaders;
import io.micronaut.messaging.annotation.Body;
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
            final String headerName = context.getAnnotation(Header.class).getRequiredValue(String.class);
            return () -> Optional.ofNullable(JMSHeaders.getHeader(headerName, source, context.getArgument().getType()));
        }
        return Optional::empty;
    }

    @Override
    public boolean canBind(Argument<?> argument) {
        return argument.isAnnotationPresent(Header.class);
    }
}
