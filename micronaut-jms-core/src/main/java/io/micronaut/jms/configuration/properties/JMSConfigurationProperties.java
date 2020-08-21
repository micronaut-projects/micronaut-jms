package io.micronaut.jms.configuration.properties;

import io.micronaut.context.annotation.ConfigurationProperties;

/***
 * Generic configuration properties for global Micronaut JMS properties.
 *
 * Existing properties include:
 * - initialPoolSize: this defines the default size of the {@link io.micronaut.jms.pool.JMSConnectionPool},
 *      {@link io.micronaut.jms.pool.SessionPool}, and {@link io.micronaut.jms.pool.MessageProducerPool}.
 * - maxPoolSize: this defines the maximum size of the {@link io.micronaut.jms.pool.JMSConnectionPool},
 *       {@link io.micronaut.jms.pool.SessionPool}, and {@link io.micronaut.jms.pool.MessageProducerPool}.
 *
 * @author elliott
 * @since 1.0
 */
@ConfigurationProperties("micronaut.jms")
public class JMSConfigurationProperties {
    private Integer initialPoolSize = 1;
    private Integer maxPoolSize = 50;

    public Integer getInitialPoolSize() {
        return initialPoolSize;
    }

    public void setInitialPoolSize(Integer initialPoolSize) {
        this.initialPoolSize = initialPoolSize;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }
}
