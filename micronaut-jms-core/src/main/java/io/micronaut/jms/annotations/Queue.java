package io.micronaut.jms.annotations;

import io.micronaut.jms.serdes.DefaultSerializerDeserializer;
import io.micronaut.jms.serdes.Deserializer;

import javax.jms.Session;
import java.lang.annotation.*;

/***
 *
 * Annotation required to bind a {@link javax.jms.Queue} to a method for receiving or sending a {@link javax.jms.Message}.
 *
 * Usage:
 * <pre>
 *      {@code
 * @JMSListener("myConnectionFactory")
 * public class Listener {
 *      @Queue(
 *          destination = "my-queue",
 *          executor = "micronaut-executor-service"
 *      )
 *      public <T> void handle(T body, @Header(JMSHeaders.JMS_MESSAGE_ID) String messageID) {
 *          // do some logic with body and messageID
 *      }
 *
 *      @Queue(
 *          destination = "my-queue-2",
 *          concurrency = "1-5",
 *          transacted = true,
 *          acknowledgement = Session.CLIENT_ACKNOWLEDGE
 *      )
 *      public <T> void handle(T body, @Header("X-Arbitrary-Header") String arbitraryHeader) {
 *          // do some logic with body and arbitraryHeader
 *      }
 * }
 *      }
 * </pre>
 *
 * @author elliott
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Queue {
    String destination();
    Class<? extends Deserializer> deserializer() default DefaultSerializerDeserializer.class;
    String concurrency() default "1-1";
    String executor() default "";
    int acknowledgement() default Session.AUTO_ACKNOWLEDGE;
    boolean transacted() default false;
}
