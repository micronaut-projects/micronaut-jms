package io.micronaut.jms.configuration;

import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.bind.BoundExecutable;
import io.micronaut.core.bind.DefaultExecutableBinder;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Topic;
import io.micronaut.jms.bind.JMSArgumentBinderRegistry;
import io.micronaut.jms.listener.JMSListenerContainerFactory;
import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/***
 *
 * Method post processor that scans all beans annotated with {@link JMSListener} and sets up
 *  {@link io.micronaut.jms.listener.JMSListenerContainer}.
 *
 *
 * @author elliott
 */
@Singleton
public class JMSListenerMethodProcessor implements ExecutableMethodProcessor<JMSListener> {

    @Inject
    private BeanContext beanContext;

    @Inject
    private ApplicationContext applicationContext;

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        AnnotationValue<Queue> queueAnnotation = method.getAnnotation(Queue.class);
        AnnotationValue<JMSListener> listenerAnnotation = beanDefinition.getAnnotation(JMSListener.class);

        if (queueAnnotation != null) {
            if (Stream.of(method.getArguments())
                    .filter(arg -> arg.getAnnotationMetadata().isEmpty())
                    .count() > 1) {
                throw new IllegalStateException("Queue annotated method can have only one argument without an annotation.");
            }
            final Class<?> targetClass = Stream.of(method.getArguments())
                    .filter(arg -> arg.getAnnotationMetadata().isEmpty())
                    .findAny()
                    .map(Argument::getClass)
                    .get();
            final String destination = queueAnnotation.getRequiredValue("destination", String.class);

            final Optional<String> executorServiceName = queueAnnotation.get("executor", String.class);
            final Optional<String> concurrency = queueAnnotation.get("concurrency", String.class);
            final Optional<Integer> acknowledgment = queueAnnotation.get("acknowledgement", Integer.class);
            final Optional<Boolean> transacted = queueAnnotation.get("transacted", Boolean.class);

            final ExecutorService executor;
            if (executorServiceName.isPresent() && !executorServiceName.get().isEmpty()) {
                executor = beanContext.findBean(ExecutorService.class, Qualifiers.byName(executorServiceName.get()))
                        .orElseThrow(() -> new IllegalStateException("There is no configured executor service for " + executorServiceName.get()));
            } else {
                final Pattern concurrencyPattern = Pattern.compile("([0-9]+)-([0-9]+)");
                final Matcher matcher = concurrencyPattern.matcher(
                        concurrency.orElseThrow(() -> new IllegalStateException("If executor is not specified then concurrency must be specified")));
                if (matcher.find() && matcher.groupCount() == 2) {
                    int numThreads = Integer.parseInt(matcher.group(1));
                    int maxThreads = Integer.parseInt(matcher.group(2));
                    executor = new ThreadPoolExecutor(
                            numThreads,
                            maxThreads,
                            500L,
                            TimeUnit.MILLISECONDS,
                            new LinkedBlockingQueue<>(numThreads),
                            new NamedThreadFactory(destination + "-pool-1-thread"));

                } else {
                    throw new IllegalArgumentException("Concurrency must be of the form int-int (i.e. \"1-10\"). Concurrency provided was " + concurrency.get());
                }
            }

            final String connectionFactoryName = Objects.requireNonNull(listenerAnnotation).getRequiredValue("connectionFactory", String.class);

            final JMSListenerContainerFactory listenerFactory = beanContext.findBean(JMSListenerContainerFactory.class).orElseThrow(
                    () -> new IllegalStateException("No JMSListenerFactory configured"));

            final JMSConnectionPool JMSConnectionPool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactoryName));

            final Object bean = beanContext.findBean(beanDefinition.getBeanType()).get();

            final DefaultExecutableBinder<Message> binder = new DefaultExecutableBinder<>();

            final MessageListener listener = message -> {
                executor.submit(() -> {
                    BoundExecutable boundExecutable = binder.bind(method, new JMSArgumentBinderRegistry(), message);
                    boundExecutable.invoke(bean);
                    if (Session.CLIENT_ACKNOWLEDGE == acknowledgment.orElse(Session.AUTO_ACKNOWLEDGE)) {
                        try {
                            message.acknowledge();
                        } catch (JMSException e) {
                            e.printStackTrace();
                        }
                    }
                });
            };

            listenerFactory.getJMSListener(
                    JMSConnectionPool,
                    destination,
                    listener,
                    targetClass,
                    transacted.orElse(false),
                    acknowledgment.orElse(Session.AUTO_ACKNOWLEDGE),
                    JMSDestinationType.QUEUE);
            return;
        }

        AnnotationValue<Topic> topicAnnotation = method.getAnnotation(Topic.class);

        if (topicAnnotation != null) {
            if (Stream.of(method.getArguments())
                    .filter(arg -> arg.getAnnotationMetadata().isEmpty())
                    .count() > 1) {
                throw new IllegalStateException("Queue annotated method can have only one argument without an annotation.");
            }
            final Class<?> targetClass = Stream.of(method.getArguments())
                    .filter(arg -> arg.getAnnotationMetadata().isEmpty())
                    .findAny()
                    .map(Argument::getClass)
                    .get();
            final String destination = topicAnnotation.getRequiredValue("destination", String.class);

            final Optional<Integer> acknowledgment = topicAnnotation.get("acknowledgement", Integer.class);
            final Optional<Boolean> transacted = topicAnnotation.get("transacted", Boolean.class);

            final ExecutorService executor = Executors.newSingleThreadExecutor(
                    new NamedThreadFactory(destination + "-pool-1-thread"));

            final String connectionFactoryName = Objects.requireNonNull(listenerAnnotation).getRequiredValue("connectionFactory", String.class);

            final JMSListenerContainerFactory listenerFactory = beanContext.findBean(JMSListenerContainerFactory.class).orElseThrow(
                    () -> new IllegalStateException("No JMSListenerFactory configured"));

            final JMSConnectionPool JMSConnectionPool = beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactoryName));

            final Object bean = beanContext.findBean(beanDefinition.getBeanType()).get();

            final DefaultExecutableBinder<Message> binder = new DefaultExecutableBinder<>();

            final MessageListener listener = message -> {
                executor.submit(() -> {
                    BoundExecutable boundExecutable = binder.bind(method, new JMSArgumentBinderRegistry(), message);
                    boundExecutable.invoke(bean);
                    if (Session.CLIENT_ACKNOWLEDGE == acknowledgment.orElse(Session.AUTO_ACKNOWLEDGE)) {
                        try {
                            message.acknowledge();
                        } catch (JMSException e) {
                            e.printStackTrace();
                        }
                    }
                });
            };

            listenerFactory.getJMSListener(
                    JMSConnectionPool,
                    destination,
                    listener,
                    targetClass,
                    transacted.orElse(false),
                    acknowledgment.orElse(Session.AUTO_ACKNOWLEDGE),
                    JMSDestinationType.TOPIC);
            return;
        }

        throw new IllegalStateException("JMSListener must be configured with either a Queue or a Topic method.");
    }
}

