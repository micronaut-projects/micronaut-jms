package io.micronaut.jms.dev;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * An SQS client of the runtime classpath, outside the reloadable classes of the application, that copies its
 * settings rather than reading the environment: one the SQS connection factory can be retained with.
 */
@Factory
@Requires(property = RuntimeSqsClientFactory.ENDPOINT)
class RuntimeSqsClientFactory {

    static final String ENDPOINT = "dev-reload.sqs.endpoint";

    @Singleton
    @Replaces(SqsClient.class)
    @Bean(preDestroy = "close")
    SqsClient sqsClient(@Value("${" + ENDPOINT + "}") String endpoint,
                        @Value("${dev-reload.sqs.region}") String region,
                        @Value("${dev-reload.sqs.access-key}") String accessKey,
                        @Value("${dev-reload.sqs.secret-key}") String secretKey) {
        return SqsClient.builder()
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .region(Region.of(region))
            .build();
    }
}
