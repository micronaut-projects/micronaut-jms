package example;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@Disabled
@MicronautTest
class TasksTest {

    @Test
    void testShouldProcessTasks(@Client("/") HttpClient client) {
        await().atMost(30, SECONDS).until(() ->
            {
                Integer result = client.toBlocking().retrieve("/tasks/processed-count", Integer.class);
                return result != null && result > 3;
            }
        );
    }

}
