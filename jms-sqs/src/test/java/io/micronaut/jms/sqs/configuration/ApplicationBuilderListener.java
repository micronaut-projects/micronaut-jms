package io.micronaut.jms.sqs.configuration;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * Customizes the SQS client builder, as a listener of the application may, with something the client does not tell it
 * holds: the tests treat its class as a class of the application.
 */
@Singleton
@Requires(property = "test.sqs.builder-listener", value = "true")
public class ApplicationBuilderListener implements BeanCreatedEventListener<SqsClientBuilder> {
    @Override
    public SqsClientBuilder onCreated(BeanCreatedEvent<SqsClientBuilder> event) {
        return event.getBean().overrideConfiguration(configuration -> configuration.putHeader("x-application", "listener"));
    }
}
