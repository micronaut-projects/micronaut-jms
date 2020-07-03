package io.micronaut.jms.pool;

import io.micronaut.context.annotation.Context;

import javax.inject.Inject;
import javax.jms.Connection;

/***
 * Factory for generating a {@link SessionPool} from a {@link Connection}.
 *
 * @see JMSConnectionPool
 * @see PooledConnection
 *
 * @author elliott
 */
@Context
public class SessionPoolFactory {

    @Inject
    private MessageProducerPoolFactory producerPoolFactory;

    /***
     * Returns a {@link SessionPool} from the provided {@param connection}.
     *
     * @param connection
     * @return a {@link SessionPool} from the provided {@param connection}.
     */
    public SessionPool getSessionPool(Connection connection) {
        return new SessionPool(1, 20, connection, producerPoolFactory);
    }
}
