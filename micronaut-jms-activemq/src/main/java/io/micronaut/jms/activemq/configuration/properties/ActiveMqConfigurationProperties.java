package io.micronaut.jms.activemq.configuration.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@ConfigurationProperties("io.micronaut.configuration.jms.activemq")
@Data
@Requires(property = "io.micronaut.configuration.jms.activemq.enabled", value = "true")
public class ActiveMqConfigurationProperties {
    private boolean enabled;
    @NotBlank
    private String connectionString;
}
