package io.micronaut.jms.pool;

import javax.jms.JMSException;

public abstract class PooledObject<T> implements AutoCloseable {
    private final AbstractPool<PooledObject<T>> pool;
    protected final T object;

    public PooledObject(AbstractPool<PooledObject<T>> pool, T object) {
        this.pool = pool;
        this.object = object;
    }

    public T get() {
        return object;
    }

    @Override
    public void close() throws JMSException {
        pool.release(this);
    }
}
