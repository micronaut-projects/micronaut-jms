package io.micronaut.jms.sqs.configuration;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * An SQS client of the runtime classpath that does not read the environment, as an application could configure one
 * outside micronaut-aws, and one that holds an execution interceptor of another classloader, as a
 * {@code BeanCreatedEventListener} of the application could give it.
 */
@Factory
@Requires(property = "test.sqs.client")
class TestSqsClientFactory {

    @Singleton
    @Replaces(SqsClient.class)
    @Bean(preDestroy = "close")
    SqsClient sqsClient(@Value("${test.sqs.client}") String kind) {
        return SqsClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .overrideConfiguration(configuration -> {
                if ("application-interceptor".equals(kind)) {
                    configuration.addExecutionInterceptor(applicationInterceptor());
                }
            })
            .build();
    }

    /**
     * An interceptor whose class is defined by a classloader below the one of the SQS client, as the classes of the
     * application are in development mode.
     */
    static ExecutionInterceptor applicationInterceptor() {
        ClassLoader application = new URLClassLoader(new URL[0], TestSqsClientFactory.class.getClassLoader());
        return (ExecutionInterceptor) Proxy.newProxyInstance(application, new Class<?>[] {ExecutionInterceptor.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "applicationInterceptor";
                default -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : null;
            });
    }
}
