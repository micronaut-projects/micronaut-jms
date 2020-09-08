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

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;

import javax.inject.Singleton;
import javax.jms.Message;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/***
 * An {@link ArgumentBinderRegistry} for all implementations of {@link ArgumentBinder} capable of binding a
 *      {@link Message}.
 *
 * @see HeaderArgumentBinder
 * @see BodyArgumentBinder
 * @see AbstractChainedArgumentBinder
 * @see io.micronaut.jms.configuration.AbstractJMSListenerMethodProcessor
 *
 * @author elliott
 * @since 1.0
 */
@Singleton
public class JMSArgumentBinderRegistry implements ArgumentBinderRegistry<Message> {

    private final List<AbstractChainedArgumentBinder> argumentBinderChain = new LinkedList<>();
    private final AbstractChainedArgumentBinder defaultArgumentBinder = new AbstractChainedArgumentBinder() {
        @Override
        public boolean canBind(Argument<?> argument) {
            return false;
        }

        @Override
        public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Message source) {
            throw new IllegalArgumentException("Cannot bind argument " + context.getArgument().getName());
        }
    };

    public JMSArgumentBinderRegistry() {
        argumentBinderChain.add(new HeaderArgumentBinder());
        argumentBinderChain.add(new BodyArgumentBinder());
    }

    /***
     *
     * Adds an {@link AbstractChainedArgumentBinder} to the chain. If no argument binder is found then the
     *      default argument binder (an {@link BodyArgumentBinder}) is used
     *
     * @param argumentBinder
     */
    public void addArgumentBinder(AbstractChainedArgumentBinder argumentBinder) {
        argumentBinderChain.add(argumentBinder);
    }

    @Override
    public <T> Optional<ArgumentBinder<T, Message>> findArgumentBinder(Argument<T> argument, Message source) {
        return Optional.of((ArgumentBinder<T, Message>) argumentBinderChain.stream()
                .filter(binder -> binder.canBind(argument))
                .findFirst()
                .orElse(defaultArgumentBinder));
    }
}
