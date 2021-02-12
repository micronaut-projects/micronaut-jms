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
package io.micronaut.jms.listener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A {@link MessageHandler} decorator that wraps a delegate implementation in
 * an {@link ExecutorService} to handle many incoming messages concurrently on
 * different threads. A default single-threaded executor is provided if no
 * {@link ExecutorService} is provided.
 *
 * @param <T> the type of the object that the handler is expecting.
 * @author Elliott Pope
 * @since 1.0.0
 * @deprecated since 1.0.0.M2. All associated logic is handled by the {@link JMSListener}.
 */
@Deprecated
public class ConcurrentMessageHandler<T> implements MessageHandler<T> {

    private static final long DEFAULT_AWAIT_TIMEOUT = 10; // TODO configurable

    private final MessageHandler<T> delegate;
    private final ExecutorService executorService;

    /**
     * Allows for concurrent handling of messages by handing of the logic to
     * a delegate {@link MessageHandler} but wrapping those calls within
     * an {@link ExecutorService#execute(Runnable)}.
     *
     * @param delegate        the {@link MessageHandler} to actually handle the incoming messages
     * @param executorService the {@link ExecutorService} to submit handling requests to.
     */
    public ConcurrentMessageHandler(MessageHandler<T> delegate,
                                    ExecutorService executorService) {
        this.delegate = delegate;
        this.executorService = executorService;
    }

    /**
     * Submits incoming handling requests on a single threaded executor to
     * avoid blocking the main thread.
     *
     * @param delegate the {@link MessageHandler} to actually handle the incoming messages/
     */
    public ConcurrentMessageHandler(MessageHandler<T> delegate) {
        this(delegate, Executors.newSingleThreadExecutor());
    }

    @Override
    public void handle(T message) {
        executorService.submit(() -> delegate.handle(message));
    }

    /**
     * Closes the MessageHandler and terminates the {@link ConcurrentMessageHandler#executorService}.
     *
     * @return true if the {@link ConcurrentMessageHandler#executorService} is successfully shut down.
     * @throws InterruptedException if there is a timeout waiting for the executor service to shut down
     */
    public boolean shutdown() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(DEFAULT_AWAIT_TIMEOUT, SECONDS);
        return executorService.isShutdown();
    }
}
