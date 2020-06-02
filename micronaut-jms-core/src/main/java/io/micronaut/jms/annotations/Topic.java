package io.micronaut.jms.annotations;

import java.lang.annotation.*;

/***
 *
 * Annotation required to bind a {@link javax.jms.Topic} to a method for receiving or sending a {@link javax.jms.Message}
 *
 * NOTE: This annotation does not yet work. It is a placeholder for Topic support.
 *
 * Usage:
 * <pre>
 *      {@code
 * @JMSListener("myConnectionFactory")
 * public class Listener {
 *      @Topic("my-queue")
 *      public <T> void handle(T body, @Header(JMSHeaders.JMS_MESSAGE_ID) String messageID) {
 *          // do some logic with body and messageID
 *      }
 *
 *      @Topic("my-queue-2")
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
public @interface Topic {
    String destination();
}
