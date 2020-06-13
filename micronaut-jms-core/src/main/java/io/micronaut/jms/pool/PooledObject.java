package io.micronaut.jms.pool;

import javax.jms.JMSException;

public abstract class PooledObject<T> implements AutoCloseable {

    protected final T object;
    private final AbstractPool<PooledObject<T>> pool;

    public PooledObject(AbstractPool<PooledObject<T>> pool, T object) {
        this.pool = pool;
        this.object = object;
    }

    /***
     * Retrieve the underlying object that has been pooled.
     *
     * @return the underlying object that has been pooled.
     */
    public T get() {
        return object;
    }

    @Override
    public void close() throws JMSException {
        pool.release(this);
    }
}
