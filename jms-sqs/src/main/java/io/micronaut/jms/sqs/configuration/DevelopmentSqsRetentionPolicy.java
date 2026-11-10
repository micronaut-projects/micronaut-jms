/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.jms.sqs.configuration;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDependencyGraph;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.ConfigurableBeanContext;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.reload.BeanRetentionPolicy;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static io.micronaut.jms.sqs.configuration.properties.SqsConfigurationProperties.PREFIX;

/**
 * Decides, in development mode only, whether the SQS connection factory and pool that {@link SqsConfiguration} and
 * {@link JmsConnectionPoolSQSConnectionFactory} declare {@link io.micronaut.context.annotation.Retain retained} may
 * survive a restart. It refuses:
 *
 * <ul>
 *     <li>an {@link SqsClient}, and so the connection factory and the pool that hold it, whose class, credentials,
 *     endpoint or auth scheme provider, or execution interceptors, metric publishers, retry strategy or scheduled
 *     executor of its override configuration, are classes of the application, which a
 *     {@code BeanCreatedEventListener} of the application may give it: the retained client would keep running them,
 *     and the retired generation reachable, after the restart replaced them;</li>
 *     <li>the connection factory made from an {@link SqsClientBuilder}, which builds a client from it for each
 *     connection: a builder does not tell what providers it was given;</li>
 *     <li>an {@link SqsClientBuilder} or {@link SqsClient} made from a bean that a {@code BeanCreatedEventListener} or
 *     {@code BeanInitializedEventListener} of the application applies to, such as the builder, whatever the listener
 *     gave it: a builder does not tell what providers it was given, and the client tells only some of what it holds.
 *     This is decided from the definitions of the context and its dependency graph, so it holds whether or not
 *     micronaut-aws has development support of its own;</li>
 *     <li>the connection factory and the pool when a bean they hold, such as the credentials and region providers of
 *     micronaut-aws, received the environment or the context, which stop with the context. The context refuses them
 *     too, but warns on every restart; this is how they are made by default, so it is refused here and said at
 *     debug level.</li>
 * </ul>
 *
 * <p>It abstains on every other bean. It exists only in development mode, so nothing of it is on the path of a
 * message.</p>
 *
 * @author graemerocher
 * @since 5.2.0
 */
@Internal
@Singleton
@DevelopmentActive
@Requires(property = PREFIX + ".enabled", value = StringUtils.TRUE)
final class DevelopmentSqsRetentionPolicy implements BeanRetentionPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentSqsRetentionPolicy.class);

    private final BeanContext beanContext;
    private final Predicate<Class<?>> isApplicationClass;
    private ApplicationListeners listeners;

    /**
     * @param beanContext The context, whose dependency graph tells what a retained bean holds
     */
    @Inject
    DevelopmentSqsRetentionPolicy(BeanContext beanContext) {
        this(beanContext, DevelopmentSqsRetentionPolicy::isApplicationClass);
    }

    /**
     * @param beanContext The context, whose dependency graph tells what a retained bean holds
     * @param isApplicationClass Whether a class is of the application, which the restart replaces
     */
    DevelopmentSqsRetentionPolicy(BeanContext beanContext, Predicate<Class<?>> isApplicationClass) {
        this.beanContext = beanContext;
        this.isApplicationClass = isApplicationClass;
    }

    @Override
    public Decision decide(BeanRegistration<?> registration) {
        Object bean = registration.bean();
        String applicationClass = null;
        if (bean instanceof SqsClient client) {
            applicationClass = applicationClassOf(client);
        }
        if (applicationClass != null) {
            LOG.debug("The SQS client [{}] is not retained across the restart: it holds the class [{}] of the application", bean, applicationClass);
            return Decision.REFUSE;
        }
        if (bean instanceof SqsClient || bean instanceof SqsClientBuilder) {
            // what a listener of the application gave the builder is not injected, and a builder does not tell it
            String customized = listeners().customizing(registration.getBeanDefinition());
            if (customized != null) {
                LOG.debug("The SQS client or builder [{}] is not retained across the restart: {}", bean, customized);
                return Decision.REFUSE;
            }
        }
        if (isMadeFromBuilder(registration.getBeanDefinition())) {
            // it builds a client from the builder for each connection, which no policy sees
            LOG.debug("The SQS connection factory [{}] is not retained across the restart: it builds its clients from a builder, whose providers cannot be read back", bean);
            return Decision.REFUSE;
        }
        if (isRetainedHere(registration.getBeanDefinition())) {
            BeanDefinition<?> bound = contextBoundDependency(registration);
            if (bound != null) {
                LOG.debug("The SQS bean [{}] is not retained across the restart: the bean [{}] it holds received the environment or the context of the stopped application",
                    bean, bound);
                return Decision.REFUSE;
            }
        }
        return Decision.ABSTAIN;
    }

    private synchronized ApplicationListeners listeners() {
        ApplicationListeners found = listeners;
        if (found == null) {
            // read as the context stops, which is when the policy is asked, so that a listener added meanwhile counts
            found = new ApplicationListeners(beanContext, isApplicationClass);
            listeners = found;
        }
        return found;
    }

    /**
     * Whether a definition is one this module retains: an SQS connection factory of {@link SqsConfiguration}, or the
     * pool {@link JmsConnectionPoolSQSConnectionFactory} makes from one.
     */
    private static boolean isRetainedHere(BeanDefinition<?> definition) {
        Optional<Class<?>> declaringType = definition.getDeclaringType();
        return declaringType.filter(type -> type == SqsConfiguration.class || type == JmsConnectionPoolSQSConnectionFactory.class).isPresent();
    }

    /**
     * Whether a definition is the connection factory {@link SqsConfiguration} makes from an {@link SqsClientBuilder},
     * which it exposes as a {@link ConnectionFactory} rather than as the {@code SQSConnectionFactory} it makes from a
     * client.
     */
    private static boolean isMadeFromBuilder(BeanDefinition<?> definition) {
        return definition.getBeanType() == ConnectionFactory.class
            && definition.getDeclaringType().filter(type -> type == SqsConfiguration.class).isPresent();
    }

    /**
     * The first bean the registration holds, directly or through what it holds, that received what stops with the
     * context: its environment, or the context itself. A configuration bean is passed over, as the context does: it is
     * not retained with the bean but bound again by the next context.
     */
    private BeanDefinition<?> contextBoundDependency(BeanRegistration<?> registration) {
        if (!(beanContext instanceof ConfigurableBeanContext configurable)) {
            return null;
        }
        BeanDependencyGraph graph = configurable.findDependencyGraph().orElse(null);
        if (graph == null) {
            return null;
        }
        Set<BeanDefinition<?>> visited = new HashSet<>();
        Deque<BeanDefinition<?>> pending = new ArrayDeque<>();
        pending.add(registration.getBeanDefinition());
        while (!pending.isEmpty()) {
            BeanDefinition<?> definition = pending.poll();
            if (!visited.add(definition)) {
                continue;
            }
            if (definition != registration.getBeanDefinition()) {
                if (definition.hasStereotype(ConfigurationReader.class)) {
                    continue;
                }
                for (Class<?> required : definition.getRequiredComponents()) {
                    if (PropertyResolver.class.isAssignableFrom(required) || BeanLocator.class.isAssignableFrom(required)) {
                        return definition;
                    }
                }
            }
            for (BeanDependencyGraph.BeanDependency dependency : graph.dependenciesOf(definition)) {
                if (!dependency.lazy()) {
                    pending.add(dependency.dependency());
                }
            }
        }
        return null;
    }

    private String applicationClassOf(SqsClient client) {
        List<Object> held = new ArrayList<>();
        held.add(client);
        try {
            held.add(client.serviceClientConfiguration().credentialsProvider());
            held.add(client.serviceClientConfiguration().endpointProvider().orElse(null));
            held.add(client.serviceClientConfiguration().authSchemeProvider());
            held.addAll(overrides(client.serviceClientConfiguration().overrideConfiguration()));
        } catch (RuntimeException e) {
            // a client of the application that does not expose its configuration
            return client.getClass().getName();
        }
        return applicationClassOf(held);
    }

    /**
     * What the override configuration of a client holds that an application may implement: its execution
     * interceptors, metric publishers, retry strategy and scheduled executor.
     */
    private static List<Object> overrides(ClientOverrideConfiguration configuration) {
        if (configuration == null) {
            return List.of();
        }
        List<Object> overrides = new ArrayList<>(configuration.executionInterceptors());
        overrides.addAll(configuration.metricPublishers());
        configuration.retryStrategy().ifPresent(overrides::add);
        configuration.scheduledExecutorService().ifPresent(overrides::add);
        return overrides;
    }

    private String applicationClassOf(List<Object> held) {
        for (Object object : held) {
            if (object != null && isApplicationClass.test(object.getClass())) {
                return object.getClass().getName();
            }
        }
        return null;
    }

    /**
     * Whether a class was loaded by neither the classloader of the SQS client nor one of its parents.
     */
    private static boolean isApplicationClass(Class<?> type) {
        ClassLoader loader = type.getClassLoader();
        if (loader == null) {
            return false;
        }
        for (ClassLoader sqs = SqsClient.class.getClassLoader(); sqs != null; sqs = sqs.getParent()) {
            if (sqs == loader) {
                return false;
            }
        }
        return true;
    }
}
