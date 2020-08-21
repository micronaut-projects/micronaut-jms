package io.micronaut.jms.configuration;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.jms.configuration.properties.JMSConfigurationProperties;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.pool.SessionPoolFactory;

import javax.jms.ConnectionFactory;

/***
 *
 * Factory for generating {@link JMSConnectionPool} from each registered {@link ConnectionFactory} in the context.
 *
 * @author elliott
 * @since 1.0
 */
@Context
@Factory
public class JMSConnectionFactoryBeanProcessor {

    private final JMSConfigurationProperties properties;

    public JMSConnectionFactoryBeanProcessor(JMSConfigurationProperties properties) {
        this.properties = properties;
    }

    @EachBean(ConnectionFactory.class)
    public JMSConnectionPool connectionPool(ConnectionFactory connectionFactory) {
        return new JMSConnectionPool(
                connectionFactory,
                new SessionPoolFactory(),
                properties.getInitialPoolSize(),
                properties.getMaxPoolSize());
    }
}
