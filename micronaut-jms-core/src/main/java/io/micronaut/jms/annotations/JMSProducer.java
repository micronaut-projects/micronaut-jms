package io.micronaut.jms.annotations;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Executable;

import javax.inject.Singleton;
import java.lang.annotation.*;

/***
 *
 * Annotation for declaring a class for post-processing. Any class annotated with {@link JMSProducer} must contain at
 *  least one method annotated with {@link Queue} or {@link Topic} or else an {@link IllegalStateException} will be thrown.
 *
 * Additionally, the value specified by the {@link JMSProducer} must correspond to exactly one {@link JMSConnectionFactory}
 *  or else an {@link IllegalStateException} will be thrown.
 *
 * NOTE: This annotation does not currently work and is a place-holder for a future feature.
 *
 * Usage:
 * <pre>
 *     {@code
 * @JMSConnectionFactory("connectionFactoryOne")
 * public ConnectionFactory connectionFactoryOne() {
 *     return new ActiveMqConnectionFactory("vm://localhost?broker.persist=false");
 * }
 *
 *
 * @JMSConnectionFactory("connectionFactoryTwo")
 * public ConnectionFactory connectionFactoryTwo() {
 *     RMQConnectionFactory connectionFactory = new RMQConnectionFactory();
 *     connectionFactory.setUsername("guest");
 *     connectionFactory.setPassword("guest");
 *     connectionFactory.setVirtualHost("/");
 *     connectionFactory.setHost("localhost");
 *     connectionFactory.setPort(5672);
 *     return connectionFactory;
 * }
 *
 * @JMSProducer("connectionFactoryOne")
 * public static class ProducerOne {
 *      @Queue("my-activemq-queue")
 *      public void send(String message) {
 *          // do logic
 *      }
 * }
 *
 * @JMSProducer("connectionFactoryTwo")
 * public static class ProducerTwo {
 *      @Queue("my-rabbitmq-queue")
 *      public void notify(Integer message) {
 *          // do logic
 *      }
 * }
 *     }
 * </pre>
 *
 * @author elliott
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Bean
@Executable(processOnStartup = true)
@DefaultScope(Singleton.class)
public @interface JMSProducer {
    String connectionFactory();
}
