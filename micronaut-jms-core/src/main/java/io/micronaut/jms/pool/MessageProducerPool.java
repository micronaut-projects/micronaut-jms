package io.micronaut.jms.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

/***
 *
 * Pool for ensuring maximum reuse of {@link MessageProducer} objects.
 *
 * @see PooledProducer
 * @see MessageProducerPoolFactory
 * @see SessionPool
 *
 * @author elliott
 */
public class MessageProducerPool extends AbstractPool<PooledObject<MessageProducer>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageProducerPool.class);

    private final Session session;

    public MessageProducerPool(Integer initialSize, Integer maxSize, Session session) {
        super(initialSize, maxSize);
        this.session = session;
    }

    @Override
    protected PooledObject<MessageProducer> create(Object... args) {
        try {
            assert args.length == 1;
            assert Destination.class.isAssignableFrom(args[0].getClass());
            return new PooledProducer(this, session.createProducer((Destination) args[0]));
        } catch (JMSException e) {
            LOGGER.error("failed to create Producer for pool.", e);
            return null;
        }
    }

    @Override
    protected void reset(PooledObject<MessageProducer> pooledObject) {
        // TODO: implement sensible actions to reset the MessageProducer to avoid rolling over specialized configuration.
    }
}
