/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.jms.listener;

import javax.jms.Message;
import javax.jms.Session;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;

/**
 *
 * Handles any errors thrown when handling a message on a {@link JMSListener}.
 *
 * @author Elliott Pope
 * @since 3.0.0
 */
@FunctionalInterface
public interface JMSListenerErrorHandler extends Ordered {
    /**
     * Handles the exception thrown during message processing.
     * @param session - the {@link Session} the {@link JMSListener} is bound to.
     * @param message - the {@link Message} that was processed.
     * @param ex - the exception that was thrown.
     */
    void handle(@NonNull Session session, @NonNull Message message, @NonNull Throwable ex);
}
