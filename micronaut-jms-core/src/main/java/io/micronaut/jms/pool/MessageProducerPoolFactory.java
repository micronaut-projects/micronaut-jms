package io.micronaut.jms.pool;

import io.micronaut.context.annotation.Context;

import javax.jms.Session;

/***
 *
 * Factory for generating {@link MessageProducerPool} from a {@link javax.jms.Session}.
 *
 * @see MessageProducerPool
 * @see PooledSession
 * @see PooledProducer
 *
 * @author elliott
 */
@Context
public class MessageProducerPoolFactory {
    /***
     * Generates and configures a {@link MessageProducerPool} given a {@link Session}.
     *
     * @param session
     * @return a {@link MessageProducerPool} from the provided {@param session}
     */
    public MessageProducerPool getProducerPool(Session session) {
        return new MessageProducerPool(1, 20, session);
    }
}
