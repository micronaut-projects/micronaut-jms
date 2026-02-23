package example;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TasksTest implements TestPropertyProvider {

    @Test
    void testShouldProcessTasks(@Client("/") HttpClient client) {
        await().atMost(30, SECONDS).until(() ->
            {
                Integer result = client.toBlocking().retrieve("/tasks/processed-count", Integer.class);
                return result != null && result > 3;
            }
        );
    }

    @Override
    public @NonNull Map<String, String> getProperties() {
        return LocalStack.getProperties();
    }
}
