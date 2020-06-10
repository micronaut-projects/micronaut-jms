package io.micronaut.jms.configuration;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.processor.BeanDefinitionProcessor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jms.annotations.JMSConnectionFactory;
import io.micronaut.jms.pool.JMSConnectionPool;
import io.micronaut.jms.pool.SessionPoolFactory;

import javax.jms.ConnectionFactory;

/***
 *
 * Bean post-processor to register the {@link JMSConnectionPool}
 *      which wraps the {@link ConnectionFactory} annotated with
 *      {@link JMSConnectionFactory}.
 *
 * @author elliott
 */
@Context
public class JMSConnectionFactoryBeanProcessor implements BeanDefinitionProcessor<JMSConnectionFactory> {
    @Override
    public void process(BeanDefinition<?> beanDefinition, BeanContext context) {
        final Object candidate = context.getBean(beanDefinition);
        if (candidate.getClass().isAssignableFrom(ConnectionFactory.class)) {
            throw new IllegalStateException("@JMSConnectionFactory can only be applied to a bean of type javax.jms.ConnectionFactory. " +
                    "Provided class was " + candidate.getClass().getName());
        }
        final ConnectionFactory connectionFactory = (ConnectionFactory) candidate;
        final String name = beanDefinition.getAnnotation(JMSConnectionFactory.class)
                .getRequiredValue(String.class);
        final SessionPoolFactory sessionPoolFactory = context.getBean(SessionPoolFactory.class);
        context.registerSingleton(
                JMSConnectionPool.class,
                new JMSConnectionPool(
                        connectionFactory,
                        sessionPoolFactory,
                        10,
                        20),
                Qualifiers.byName(name));
    }
}
