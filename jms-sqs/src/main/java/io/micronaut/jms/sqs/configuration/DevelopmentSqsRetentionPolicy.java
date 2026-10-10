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
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.jms.sqs.configuration.properties.SqsConfigurationProperties.PREFIX;

/**
 * Decides, in development mode only, whether the SQS connection factory and pool that {@link SqsConfiguration} and
 * {@link JmsConnectionPoolSQSConnectionFactory} declare {@link io.micronaut.context.annotation.Retain retained} may
 * survive a restart. It refuses:
 *
 * <ul>
 *     <li>an {@link SqsClient}, and so the connection factory and the pool that hold it, whose class, credentials,
 *     endpoint or auth scheme provider or execution interceptors are classes of the application, which a
 *     {@code BeanCreatedEventListener} of the application may give it: the retained client would keep running them,
 *     and the retired generation reachable, after the restart replaced them;</li>
 *     <li>an {@link SqsClientBuilder}, and so the connection factory made from it, which builds a client from it for
 *     each connection: a builder does not tell what providers it was given;</li>
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

    /**
     * @param beanContext The context, whose dependency graph tells what a retained bean holds
     */
    DevelopmentSqsRetentionPolicy(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public Decision decide(BeanRegistration<?> registration) {
        Object bean = registration.bean();
        String applicationClass = null;
        if (bean instanceof SqsClient client) {
            applicationClass = applicationClassOf(client);
        } else if (bean instanceof SqsClientBuilder) {
            // a builder does not tell the credentials, endpoint or auth providers a listener of the application may
            // have given it, and the connection factory made from it builds a client from it for each connection
            LOG.debug("The SQS client builder [{}] is not retained across the restart: what it was given cannot be read back", bean);
            return Decision.REFUSE;
        }
        if (applicationClass != null) {
            LOG.debug("The SQS client [{}] is not retained across the restart: it holds the class [{}] of the application", bean, applicationClass);
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

    /**
     * Whether a definition is one this module retains: an SQS connection factory of {@link SqsConfiguration}, or the
     * pool {@link JmsConnectionPoolSQSConnectionFactory} makes from one.
     */
    private static boolean isRetainedHere(BeanDefinition<?> definition) {
        Optional<Class<?>> declaringType = definition.getDeclaringType();
        return declaringType.filter(type -> type == SqsConfiguration.class || type == JmsConnectionPoolSQSConnectionFactory.class).isPresent();
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

    private static String applicationClassOf(SqsClient client) {
        List<Object> held = new ArrayList<>();
        held.add(client);
        try {
            held.add(client.serviceClientConfiguration().credentialsProvider());
            held.add(client.serviceClientConfiguration().endpointProvider().orElse(null));
            held.add(client.serviceClientConfiguration().authSchemeProvider());
            held.addAll(interceptors(client.serviceClientConfiguration().overrideConfiguration()));
        } catch (RuntimeException e) {
            // a client of the application that does not expose its configuration
            return client.getClass().getName();
        }
        return applicationClassOf(held);
    }

    private static List<ExecutionInterceptor> interceptors(ClientOverrideConfiguration configuration) {
        return configuration == null ? List.of() : configuration.executionInterceptors();
    }

    private static String applicationClassOf(List<Object> held) {
        for (Object object : held) {
            if (object != null && isApplicationClass(object.getClass())) {
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
