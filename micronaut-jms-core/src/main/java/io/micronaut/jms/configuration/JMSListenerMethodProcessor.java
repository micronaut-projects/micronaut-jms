package io.micronaut.jms.configuration;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Requires;
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
import io.micronaut.messaging.annotation.Body;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
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
@Requires(beans = {
        ConnectionFactory.class,
        JMSConnectionPool.class
})
public class JMSListenerMethodProcessor implements ExecutableMethodProcessor<JMSListener> {

    private static final String CONCURRENCY_PATTERN = "([0-9]+)-([0-9]+)";
    private static final Logger LOGGER = LoggerFactory.getLogger(JMSListenerMethodProcessor.class);

    private final BeanContext beanContext;
    private final ApplicationContext applicationContext;
    private final JMSArgumentBinderRegistry jmsArgumentBinderRegistry;
    private final DefaultExecutableBinder<Message> binder = new DefaultExecutableBinder<>();

    public JMSListenerMethodProcessor(BeanContext beanContext, ApplicationContext applicationContext, JMSArgumentBinderRegistry jmsArgumentBinderRegistry) {
        this.beanContext = beanContext;
        this.applicationContext = applicationContext;
        this.jmsArgumentBinderRegistry = jmsArgumentBinderRegistry;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        AnnotationValue<Queue> queueAnnotation = method.getAnnotation(Queue.class);
        AnnotationValue<JMSListener> listenerAnnotation = beanDefinition.getAnnotation(JMSListener.class);

        if (queueAnnotation != null) {
            String destination = queueAnnotation.stringValue()
                    .orElseThrow(() -> new IllegalStateException("@Queue annotation must specify a destination."));
            final Optional<String> executorServiceName = queueAnnotation.stringValue("executor");
            final Optional<String> concurrency = queueAnnotation.stringValue("concurrency");

            registerJMSListener(
                    method,
                    listenerAnnotation,
                    beanDefinition,
                    queueAnnotation,
                    generateExecutorService(executorServiceName, concurrency, destination),
                    JMSDestinationType.QUEUE);
            return;
        }

        AnnotationValue<Topic> topicAnnotation = method.getAnnotation(Topic.class);

        if (topicAnnotation != null) {
            String destination = topicAnnotation.stringValue()
                    .orElseThrow(() -> new IllegalStateException("@Topic annotation must specify a destination."));
            registerJMSListener(
                    method,
                    listenerAnnotation,
                    beanDefinition,
                    topicAnnotation,
                    Executors.newSingleThreadExecutor(),
                    JMSDestinationType.TOPIC);
            return;
        }

        throw new IllegalStateException("JMSListener must be configured with either a Queue or a Topic method.");
    }

    private static void validateArguments(ExecutableMethod<?, ?> method) {
        Stream.of(method.getArguments())
                .filter(arg -> arg.isDeclaredAnnotationPresent(Body.class))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("A method annotated with @Queue must have exactly one argument annotated with @Body"));
    }

    private ExecutorService generateExecutorService(
            Optional<String> executorName,
            Optional<String> concurrency,
            String destination) {
        if (executorName.isPresent() && !executorName.get().isEmpty()) {
            return beanContext.findBean(ExecutorService.class, Qualifiers.byName(executorName.get()))
                    .orElseThrow(() -> new IllegalStateException("There is no configured executor service for " + executorName.get()));
        } else {
            final Pattern concurrencyPattern = Pattern.compile(CONCURRENCY_PATTERN);
            final Matcher matcher = concurrencyPattern.matcher(
                    concurrency.orElseThrow(() -> new IllegalStateException("If executor is not specified then concurrency must be specified")));
            if (matcher.find() && matcher.groupCount() == 2) {
                int numThreads = Integer.parseInt(matcher.group(1));
                int maxThreads = Integer.parseInt(matcher.group(2));
                return new ThreadPoolExecutor(
                        numThreads,
                        maxThreads,
                        500L,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(numThreads),
                        Executors.defaultThreadFactory());

            } else {
                throw new IllegalArgumentException("Concurrency must be of the form int-int (i.e. \"1-10\"). Concurrency provided was " + concurrency.get());
            }
        }
    }

    private JMSConnectionPool generateConnectionPool(AnnotationValue<JMSListener> listenerAnnotation) {
        String connectionFactoryName = Objects.requireNonNull(listenerAnnotation).getRequiredValue("connectionFactory", String.class);
        return beanContext.getBean(JMSConnectionPool.class, Qualifiers.byName(connectionFactoryName));
    }

    private MessageListener generateAndBindListener(
            Object bean,
            ExecutableMethod<?, ?> method,
            ExecutorService executor,
            boolean acknowledge) {

        return message -> executor.submit(() -> {
            BoundExecutable boundExecutable = binder.bind(method, jmsArgumentBinderRegistry, message);
            boundExecutable.invoke(bean);
            if (acknowledge) {
                try {
                    message.acknowledge();
                } catch (JMSException e) {
                    LOGGER.error(
                            "Failed to acknowledge receipt of message with the broker. This message may be falsely retried",
                            e);
                }
            }
        });
    }

    private void registerJMSListener(
            ExecutableMethod<?, ?> method,
            AnnotationValue<JMSListener> listenerAnnotation,
            BeanDefinition<?> beanDefinition,
            AnnotationValue<?> destinationAnnotation,
            ExecutorService executor,
            JMSDestinationType type) {
        validateArguments(method);
        final Class<?> targetClass = Stream.of(method.getArguments())
                .filter(arg -> arg.isDeclaredAnnotationPresent(Body.class))
                .findAny()
                .map(Argument::getClass)
                .get();
        final String destination = destinationAnnotation.stringValue().orElseThrow(
                () -> new IllegalStateException("@Queue or @Topic must specify a destination"));

        final OptionalInt acknowledgment = destinationAnnotation.intValue("acknowledgement");
        final Optional<Boolean> transacted = destinationAnnotation.booleanValue("transacted");

        final JMSListenerContainerFactory listenerFactory = beanContext.findBean(JMSListenerContainerFactory.class).orElseThrow(
                () -> new IllegalStateException("No JMSListenerFactory configured"));

        final JMSConnectionPool JMSConnectionPool = generateConnectionPool(listenerAnnotation);

        final Object bean = beanContext.findBean(beanDefinition.getBeanType()).get();

        MessageListener listener = generateAndBindListener(bean, method, executor,
                Session.CLIENT_ACKNOWLEDGE == acknowledgment.orElse(Session.AUTO_ACKNOWLEDGE));

        listenerFactory.getJMSListener(
                JMSConnectionPool,
                destination,
                listener,
                targetClass,
                transacted.orElse(false),
                acknowledgment.orElse(Session.AUTO_ACKNOWLEDGE),
                type);
    }
}

