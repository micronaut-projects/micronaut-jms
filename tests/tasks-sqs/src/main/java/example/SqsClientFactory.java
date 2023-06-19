package example;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;
import java.net.URISyntaxException;

@Factory
@Replaces(factory = io.micronaut.aws.sdk.v2.service.sqs.SqsClientFactory.class)
public class SqsClientFactory {

    @Singleton
    SqsClientBuilder createSqsClientBuilder(SqsConfig sqsConfig) throws URISyntaxException {
        return SqsClient.builder()
            .endpointOverride(new URI(sqsConfig.getSqs().getEndpointOverride()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(sqsConfig.getAccessKeyId(), sqsConfig.getSecretKey())
                )
            )
            .region(Region.of(sqsConfig.getRegion()));
    }

    @Singleton
    SqsClient createSqsClient(SqsClientBuilder sqsClientBuilder) {
        return sqsClientBuilder.build();
    }

}
