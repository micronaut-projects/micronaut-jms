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
package io.micronaut.jms.health;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionMetaData;
import jakarta.jms.JMSException;
import org.reactivestreams.Publisher;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Reports whether a connection can be opened with each JMS connection factory, and the JMS provider of each.
 * Disable it with {@code endpoints.health.jms.enabled: false}.
 *
 * <p>Opening a connection doesn't reach the broker with every provider: an ActiveMQ Classic {@code failover:} URL
 * connects in the background, and the SQS factory makes no AWS call, so for those the indicator reports {@code UP}
 * while the broker is unreachable. The connections are opened on the blocking executor, since connecting can take as
 * long as the provider's connect timeout.</p>
 *
 * @since 5.2.0
 */
@Singleton
@Requires(classes = HealthIndicator.class)
@Requires(beans = HealthEndpoint.class)
@Requires(property = JMSHealthIndicator.ENABLED, notEquals = StringUtils.FALSE)
public final class JMSHealthIndicator implements HealthIndicator {

    /**
     * The property that disables the indicator.
     */
    public static final String ENABLED = HealthEndpoint.PREFIX + ".jms.enabled";

    private static final String NAME = "jms";

    private final BeanContext beanContext;
    private final ExecutorService executor;

    /**
     * @param beanContext The bean context, to find the JMS connection pools
     * @param executor    The blocking executor, which opens the connections off the event loop of the health endpoint
     */
    JMSHealthIndicator(BeanContext beanContext, @Named(TaskExecutors.BLOCKING) ExecutorService executor) {
        this.beanContext = beanContext;
        this.executor = executor;
    }

    @Override
    public Publisher<HealthResult> getResult() {
        // only once subscribed, and not on the calling thread, which is the event loop for the health endpoint
        return Publishers.fromCompletableFuture(() -> CompletableFuture.supplyAsync(this::check, executor));
    }

    private HealthResult check() {
        Collection<BeanRegistration<JMSConnectionPool>> pools = beanContext.getBeanRegistrations(JMSConnectionPool.class);
        Map<String, Object> details = new LinkedHashMap<>();
        HealthStatus status = HealthStatus.UP;
        for (BeanRegistration<JMSConnectionPool> pool : pools) {
            String name = pool.getIdentifier().getName();
            Map<String, Object> factory = new LinkedHashMap<>();
            // a new connection, not a pooled one, so that a broker the provider can't connect to is reported
            try (Connection connection = pool.getBean().getConnectionFactory().createConnection()) {
                ConnectionMetaData metaData = connection.getMetaData();
                factory.put("status", HealthStatus.UP.getName());
                factory.put("provider", metaData.getJMSProviderName());
                factory.put("providerVersion", metaData.getProviderVersion());
                factory.put("jmsVersion", metaData.getJMSVersion());
            } catch (JMSException | RuntimeException e) {
                status = HealthStatus.DOWN;
                factory.put("status", HealthStatus.DOWN.getName());
                factory.put("error", e.getClass().getName() + ": " + e.getMessage());
            }
            details.put(name, factory);
        }
        return HealthResult.builder(NAME, status).details(details).build();
    }

}
