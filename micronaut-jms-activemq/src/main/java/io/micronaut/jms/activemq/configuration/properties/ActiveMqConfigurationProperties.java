package io.micronaut.jms.activemq.configuration.properties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;

import javax.validation.constraints.NotBlank;

/***
 * Configuration properties for creating the {@link io.micronaut.jms.annotations.JMSConnectionFactory}.
 *
 * @see io.micronaut.jms.activemq.configuration.ActiveMqConfiguration
 *
 * @author elliott
 * @since 1.0
 */
@ConfigurationProperties("micronaut.jms.activemq")
@Requires(property = "micronaut.jms.activemq.enabled", value = "true")
public class ActiveMqConfigurationProperties {
    private boolean enabled;

    /***
     * Connection string to identify the broker URL i.e. vm://localhost, or tcp://my-activemq-cluster:61616
     */
    @NotBlank
    private String connectionString;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }
}
