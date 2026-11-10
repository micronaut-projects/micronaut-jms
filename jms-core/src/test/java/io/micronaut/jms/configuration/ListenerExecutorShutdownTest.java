package io.micronaut.jms.configuration;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.scheduling.TaskExecutors;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The executors that the deprecated {@code concurrency} and {@code executor = ""} options make a listener processor
 * create are shut down with the processor, and so with its context; an executor found as a bean is left to the context.
 */
class ListenerExecutorShutdownTest {

    @Test
    void theExecutorsAProcessorCreatedAreShutDownWhenItsContextStops() throws Exception {
        ExecutorService queueExecutor;
        ExecutorService topicExecutor;
        try (ApplicationContext context = ApplicationContext.run()) {
            queueExecutor = context.getBean(JMSQueueListenerMethodProcessor.class)
                .getExecutorService(AnnotationValue.builder(Queue.class).member("concurrency", "1-2").build());
            topicExecutor = context.getBean(JMSTopicListenerMethodProcessor.class)
                .getExecutorService(AnnotationValue.builder(Topic.class).member("executor", "").build());
            assertNotNull(queueExecutor);
            assertNotNull(topicExecutor);
            // a thread is started, which would outlive the context
            queueExecutor.submit(() -> { }).get();
            topicExecutor.submit(() -> { }).get();
            assertFalse(queueExecutor.isShutdown());
            assertFalse(topicExecutor.isShutdown());
        }
        assertTrue(queueExecutor.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(topicExecutor.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void anExecutorFoundAsABeanIsNotShutDownWithTheProcessor() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            JMSQueueListenerMethodProcessor processor = context.getBean(JMSQueueListenerMethodProcessor.class);
            ExecutorService created = processor.getExecutorService(AnnotationValue.builder(Queue.class).member("concurrency", "1-1").build());
            ExecutorService bean = processor.getExecutorService(AnnotationValue.builder(Queue.class).member("executor", TaskExecutors.IO).build());
            created.submit(() -> { }).get();

            // as a processor is destroyed when the listeners restart in development mode
            context.destroyBean(processor);

            assertTrue(created.awaitTermination(10, TimeUnit.SECONDS));
            assertFalse(bean.isShutdown());
            assertFalse(context.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO)).isShutdown());
        }
    }
}
