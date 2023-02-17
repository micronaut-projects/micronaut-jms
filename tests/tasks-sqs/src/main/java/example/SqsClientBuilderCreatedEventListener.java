package example;

import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;
import java.net.URISyntaxException;

@Singleton
public class SqsClientBuilderCreatedEventListener implements BeanCreatedEventListener<SqsClientBuilder> {

    @Inject
    @Value("${sqs-region:`http://localhost:4566`}")
    public String sqsRegion = "http://localhost:4566";

    @Inject
    @Value("${sqs-url:eu-central-1}")
    public String sqsUrl = "eu-central-1";

    @Override
    public SqsClientBuilder onCreated(BeanCreatedEvent<SqsClientBuilder> event) {
        SqsClientBuilder builder = event.getBean();
        builder.region(Region.of(sqsRegion));
        builder.credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create("foo", "bar")
            )
        );
        try {
            builder.endpointOverride(new URI(sqsUrl));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return builder;
    }
}
