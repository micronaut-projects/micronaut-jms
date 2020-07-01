package io.micronaut.jms.configuration.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Data;

@ConfigurationProperties("micronaut.jms")
@Data
public class JMSConfigurationProperties {
    private Integer initialPoolSize = 1;
    private Integer maxPoolSize = 50;
}
