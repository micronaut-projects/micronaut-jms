package io.micronaut.jms.listener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
