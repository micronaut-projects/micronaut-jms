package io.micronaut.jms.exception;

import io.micronaut.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

/***
 *
 *
 *
 * @author Elliott Pope
 * @since 1.0
 */
@Singleton
@Primary
public class DefaultJMSListenerExceptionHandler implements JMSExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultJMSListenerExceptionHandler.class);

    @Override
    public void handle(JMSException exception) {
        if (logger.isErrorEnabled()) {
            logger.error("Error occurred while processing the message", exception);
        }
    }
}
