package io.micronaut.jms.pool;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;

/***
 * Pool for ensuring maximum reuse of {@link Session}s within an application.
 *
 * @see SessionPoolFactory
 * @see PooledConnection
 *
 * @author elliott
 */
public class SessionPool extends AbstractPool<PooledObject<Session>> {

    private final Connection connection;

    public SessionPool(Integer initialSize, Integer maxSize, Connection connection) {
        super(initialSize, maxSize);
        this.connection = connection;
    }

    @Override
    protected PooledObject<Session> create(Object... args) {
        try {
            if (args == null || args.length == 0) {
                return new PooledSession(this, connection.createSession());
            } else if (args.length == 1) {
                assert Integer.class.isAssignableFrom(args[0].getClass());
                return new PooledSession(this, connection.createSession((Integer) args[0]));
            } else if (args.length == 2) {
                assert Boolean.class.isAssignableFrom(args[0].getClass());
                assert Integer.class.isAssignableFrom(args[1].getClass());
                return new PooledSession(this, connection.createSession((Boolean) args[0], (Integer) args[1]));
            }
            throw new IllegalArgumentException("Cannot create a Session from provided arguments.");
        } catch (JMSException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void reset(PooledObject<Session> pooledObject) {

    }
}
