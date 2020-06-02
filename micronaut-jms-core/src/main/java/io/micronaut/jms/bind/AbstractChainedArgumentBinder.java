package io.micronaut.jms.bind;

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.type.Argument;

import javax.jms.Message;

public abstract class AbstractChainedArgumentBinder implements ArgumentBinder<Object, Message> {
    public abstract boolean canBind(Argument<?> argument);
}
