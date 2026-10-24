package io.micronaut.jms.docs.concurrency;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.jms.Session;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.jms.activemq.classic.configuration.ActiveMqClassicConfiguration.CONNECTION_FACTORY_BEAN_NAME;
// end::imports[]

@Requires(property = "spec.name", value = "ConcurrencySpec")
// tag::clazz[]
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class JobListener {

    final Set<Session> sessions = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    final AtomicInteger processed = new AtomicInteger();

    @Queue(value = "jobs", consumers = 3) // <1>
    public void process(@MessageBody String job, Session session) throws InterruptedException {
        sessions.add(session); // <2>
        Thread.sleep(50);
        processed.incrementAndGet();
    }
}
// end::clazz[]
