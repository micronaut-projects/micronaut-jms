package io.micronaut.jms.pool;

import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;

/***
 * Wrapper for {@link Connection} that returns it to
 *      the {@link JMSConnectionPool} with a call to
 *      {@link AutoCloseable#close()}.
 *
 * @author elliott
 *
 * @see java.lang.AutoCloseable
 * @see JMSConnectionPool
 */
public class PooledConnection extends PooledObject<Connection> implements Connection {

    private final SessionPool sessionPool;

    public PooledConnection(
            Connection connection,
            AbstractPool<PooledObject<Connection>> connectionPool,
            SessionPool sessionPool) {
        super(connectionPool, connection);
        this.sessionPool = sessionPool;
    }

    @Override
    public Session createSession(boolean transacted, int acknowledgeMode) throws JMSException {
        return object.createSession(transacted, acknowledgeMode);
    }

    @Override
    public Session createSession(int sessionMode) throws JMSException {
        return object.createSession(sessionMode);
    }

    @Override
    public Session createSession() throws JMSException {
        return object.createSession();
    }

    @Override
    public String getClientID() throws JMSException {
        return object.getClientID();
    }

    @Override
    public void setClientID(String clientID) throws JMSException {
        object.setClientID(clientID);
    }

    @Override
    public ConnectionMetaData getMetaData() throws JMSException {
        return object.getMetaData();
    }

    @Override
    public ExceptionListener getExceptionListener() throws JMSException {
        return object.getExceptionListener();
    }

    @Override
    public void setExceptionListener(ExceptionListener listener) throws JMSException {
        object.setExceptionListener(listener);
    }

    @Override
    public void start() throws JMSException {
    }

    @Override
    public void stop() throws JMSException {
    }

    @Override
    public void close() throws JMSException {
        super.close();
    }

    @Override
    public ConnectionConsumer createConnectionConsumer(Destination destination, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        return object.createConnectionConsumer(destination, messageSelector, sessionPool, maxMessages);
    }

    @Override
    public ConnectionConsumer createSharedConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        return object.createSharedConnectionConsumer(topic, subscriptionName, messageSelector, sessionPool, maxMessages);
    }

    @Override
    public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        return object.createDurableConnectionConsumer(topic, subscriptionName, messageSelector, sessionPool, maxMessages);
    }

    @Override
    public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        return object.createSharedDurableConnectionConsumer(topic, subscriptionName, messageSelector, sessionPool, maxMessages);
    }

    public static PooledConnection of(PooledObject<Connection> pooledObject) {
        return (PooledConnection) pooledObject;
    }
}
