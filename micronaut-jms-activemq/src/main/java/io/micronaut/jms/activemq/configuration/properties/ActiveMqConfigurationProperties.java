package io.micronaut.jms.activemq.configuration.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@ConfigurationProperties("micronaut.jms.activemq")
@Data
@Requires(property = "micronaut.jms.activemq.enabled", value = "true")
public class ActiveMqConfigurationProperties {
    private boolean enabled;
    @NotBlank
    private String connectionString;
}
