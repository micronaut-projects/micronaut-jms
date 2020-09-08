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
import java.util.concurrent.TimeUnit;

/***
 * A {@link MessageHandler} decorator that wraps a delegate implementation in an {@link ExecutorService}
 *      so that many incoming messages can be handled concurrently on different threads. If no {@link ExecutorService}
 *      is provided then a default single threaded executor is provided.
 *
 * @param <T> - the type of the object that the handler is expecting.
 *
 * @author elliott
 * @since 1.0
 */
public class ConcurrentMessageHandler<T> implements MessageHandler<T> {

    private MessageHandler<T> delegate;
    private ExecutorService executorService;

    /***
     * Allows for concurrent handling of messages by handing of the logic to
     *      a delegate {@link MessageHandler} but wrapping those calls within
     *      an {@link ExecutorService#execute(Runnable)}.
     *
     * @param delegate - the {@link MessageHandler} to actually handle the incoming messages
     * @param executorService - the {@link ExecutorService} to submit handling requests to.
     */
    public ConcurrentMessageHandler(MessageHandler<T> delegate, ExecutorService executorService) {
        this.delegate = delegate;
        this.executorService = executorService;
    }

    /***
     * Submits incoming handling requests on a single threaded executor to avoid
     *      blocking the main thread.
     *
     * @param delegate - the {@link MessageHandler} to actually handle the incoming messages/
     */
    public ConcurrentMessageHandler(MessageHandler<T> delegate) {
        this(delegate, Executors.newSingleThreadExecutor());
    }

    /***
     *
     * Sets the {@link ExecutorService} to delegate the message handling to.
     *
     * @param executorService
     */
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public void handle(T message) {
        executorService.submit(() -> delegate.handle(message));
    }

    /***
     *
     * Closes the MessageHandler and terminates the {@link ConcurrentMessageHandler#executorService}.
     *
     * @return true if the {@link ConcurrentMessageHandler#executorService} is successfully shut down.
     *      false otherwise.
     * @throws InterruptedException
     */
    public boolean shutdown() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(10L, TimeUnit.SECONDS);
        return executorService.isShutdown();
    }
}
