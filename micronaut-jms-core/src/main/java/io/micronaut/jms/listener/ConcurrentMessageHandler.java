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

    public ConcurrentMessageHandler(MessageHandler<T> delegate, ExecutorService executorService) {
        this.delegate = delegate;
        this.executorService = executorService;
    }

    public ConcurrentMessageHandler(MessageHandler<T> delegate) {
        this.delegate = delegate;
        this.executorService = Executors.newSingleThreadExecutor();
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
