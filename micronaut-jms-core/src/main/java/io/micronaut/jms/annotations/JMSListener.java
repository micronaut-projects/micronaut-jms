package io.micronaut.jms.annotations;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.Executable;

import javax.inject.Singleton;
import java.lang.annotation.*;

/***
 *
 * Annotation for declaring a class for post-processing. Any class annotated with {@link JMSListener} must contain at
 *  least one method annotated with {@link Queue} or {@link Topic} or else an {@link IllegalStateException} will be thrown.
 *
 * Additionally, the value specified by the {@link JMSListener} must correspond to exactly one {@link JMSConnectionFactory}
 *  or else an {@link IllegalStateException} will be thrown.
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
 * @JMSListener("connectionFactoryOne")
 * public static class ListenerOne {
 *      @Queue("my-activemq-queue")
 *      public void onMessage(String message) {
 *          // do logic
 *      }
 * }
 *
 * @JMSListener("connectionFactoryTwo")
 * public static class ListenerTwo {
 *      @Queue("my-rabbitmq-queue")
 *      public void handle(Integer message) {
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
public @interface JMSListener {
    @AliasFor(member = "connectionFactory")
    String value() default "";

    @AliasFor(member = "value")
    String connectionFactory() default "";
}
