package example;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class TasksPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(TasksPublisher.class);
    private final AtomicInteger tasksIds = new AtomicInteger();
    private final TasksProducer tasksProducer;

    public TasksPublisher(TasksProducer tasksProducer) {
        this.tasksProducer = tasksProducer;
    }

    @Scheduled(initialDelay = "2s", fixedRate = "1s")
    public void sendMessages() {
        try {
            int id = tasksIds.incrementAndGet();
            LOG.info("Publishing a task with id: {}", id);
            tasksProducer.send(new Task(id), "" + id);
        } catch (Exception e) {
            LOG.error("Failed to publish a task", e);
        }
    }

}
