package io.micronaut.jms.sqs.configuration;

import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.reload.BeanRetentionPolicy;
import io.micronaut.context.reload.BeanRetentionPolicy.Decision;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.jms.pool.JMSConnectionPool;
import jakarta.jms.ConnectionFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The development-only retention policy of the SQS module: it refuses an SQS client that holds a class of the
 * application, and the retained connection factory and pool when what they hold received the environment, as the
 * client micronaut-aws builds does; it abstains otherwise.
 */
class DevelopmentSqsRetentionPolicyTest {

    @Test
    void theConnectionFactoryAndPoolOfAClientThatHoldsNothingOfTheContextAreLeftToTheirRetainAnnotation() {
        try (ApplicationContext context = developmentContext(Map.of("test.sqs.client", "runtime"))) {
            DevelopmentSqsRetentionPolicy policy = context.getBean(DevelopmentSqsRetentionPolicy.class);
            assertEquals(Decision.ABSTAIN, policy.decide(registration(context, context.getBean(SQSConnectionFactory.class))));
            assertEquals(Decision.ABSTAIN, policy.decide(registration(context, context.getBean(JMSConnectionPool.class))));
            assertEquals(Decision.ABSTAIN, policy.decide(registration(context, context.getBean(SqsClient.class))));
        }
    }

    @Test
    void aClientThatHoldsAnApplicationInterceptorIsRefused() {
        try (ApplicationContext context = developmentContext(Map.of("test.sqs.client", "application-interceptor"))) {
            DevelopmentSqsRetentionPolicy policy = context.getBean(DevelopmentSqsRetentionPolicy.class);
            assertEquals(Decision.REFUSE, policy.decide(registration(context, context.getBean(SqsClient.class))));
        }
    }

    @Test
    void aClientThatHoldsAnApplicationMetricPublisherIsRefused() {
        try (ApplicationContext context = developmentContext(Map.of("test.sqs.client", "application-metric-publisher"))) {
            DevelopmentSqsRetentionPolicy policy = context.getBean(DevelopmentSqsRetentionPolicy.class);
            assertEquals(Decision.REFUSE, policy.decide(registration(context, context.getBean(SqsClient.class))));
        }
    }

    @Test
    void withoutTheDevelopmentSupportOfMicronautAwsABuilderIsRefusedSinceWhatItWasGivenCannotBeReadBack() {
        try (ApplicationContext context = developmentContext(Map.of("aws.region", "us-east-1"))) {
            assertFalse(context.getBeanDefinitions(BeanRetentionPolicy.class).stream()
                .anyMatch(definition -> definition.getBeanType().getName().equals(DevelopmentSqsRetentionPolicy.AWS_DEVELOPMENT_POLICY)),
                "this module builds against a micronaut-aws without development support");
            DevelopmentSqsRetentionPolicy policy = context.getBean(DevelopmentSqsRetentionPolicy.class);
            assertEquals(Decision.REFUSE, policy.decide(registration(context, context.getBean(SqsClientBuilder.class))));
        }
    }

    @Test
    void theConnectionFactoryMadeFromABuilderIsRefusedSinceItBuildsItsClientsFromIt() {
        try (ApplicationContext context = developmentContext(Map.of("aws.region", "us-east-1"))) {
            DevelopmentSqsRetentionPolicy policy = context.getBean(DevelopmentSqsRetentionPolicy.class);
            BeanDefinition<ConnectionFactory> madeFromBuilder = context.getBeanDefinitions(ConnectionFactory.class).stream()
                .filter(definition -> definition.getBeanType() == ConnectionFactory.class)
                .findFirst()
                .orElseThrow();
            assertEquals(Decision.REFUSE, policy.decide(registration(context, context.getBean(madeFromBuilder))));
        }
    }

    @Test
    void theConnectionFactoryAndPoolOfTheMicronautAwsClientAreRefusedSinceItsProvidersHoldTheEnvironment() {
        try (ApplicationContext context = developmentContext(Map.of("aws.region", "us-east-1"))) {
            DevelopmentSqsRetentionPolicy policy = context.getBean(DevelopmentSqsRetentionPolicy.class);
            assertEquals(Region.US_EAST_1, context.getBean(SqsClient.class).serviceClientConfiguration().region());
            assertEquals(Decision.REFUSE, policy.decide(registration(context, context.getBean(SQSConnectionFactory.class))));
            assertEquals(Decision.REFUSE, policy.decide(registration(context, context.getBean(JMSConnectionPool.class))));
            // the client itself holds no class of the application: the context refuses it for the environment
            assertEquals(Decision.ABSTAIN, policy.decide(registration(context, context.getBean(SqsClient.class))));
        }
    }

    @Test
    void outsideDevelopmentModeThereIsNoPolicy() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("micronaut.jms.sqs.enabled", true, "test.sqs.client", "runtime"))) {
            assertFalse(context.containsBean(DevelopmentSqsRetentionPolicy.class));
        }
    }

    private static ApplicationContext developmentContext(Map<String, Object> properties) {
        Map<String, Object> all = new HashMap<>(properties);
        all.put("micronaut.dev.enabled", true);
        all.put("micronaut.jms.sqs.enabled", true);
        return ApplicationContext.builder()
            .properties(all)
            .beanDependencyTrackingEnabled(true)
            .start();
    }

    private static BeanRegistration<?> registration(ApplicationContext context, Object bean) {
        return context.findBeanRegistration(bean).orElseThrow();
    }
}
