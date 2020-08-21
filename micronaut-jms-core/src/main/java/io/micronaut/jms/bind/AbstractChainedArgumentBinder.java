package io.micronaut.jms.bind;

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.type.Argument;

import javax.jms.Message;

/***
 * Abstract method for adding having multiple registered {@link ArgumentBinder}s to handle a {@link Message}.
 *
 * @see HeaderArgumentBinder
 * @see BodyArgumentBinder
 *
 * @author elliott
 * @since 1.0
 */
public abstract class AbstractChainedArgumentBinder implements ArgumentBinder<Object, Message> {

    /***
     * @param argument - the method argument to be bound to.
     * @return true if the binder is capable of binding the {@link Message} to the {@param argument}, false otherwise.
     */
    public abstract boolean canBind(Argument<?> argument);
}
