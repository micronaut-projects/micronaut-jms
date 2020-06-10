package io.micronaut.jms.pool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractPool<T extends PooledObject<?>> {

    protected final List<T> pool;
    private final List<T> active;
    protected final Integer initialSize;
    protected final Integer maxSize;

    public AbstractPool(
            Integer initialSize,
            Integer maxSize) {
        this.pool = Collections.synchronizedList(new ArrayList<>(maxSize));
        this.active = Collections.synchronizedList(new ArrayList<>(maxSize));
        this.initialSize = initialSize;
        this.maxSize = maxSize;
    }

    public T request(Object... args) {
        if (pool.isEmpty()) {
            if (active.size() < maxSize) {
                pool.add(create(args));
            } else {
                throw new RuntimeException("Max Pool size reached.");
            }
        }
        T object = pool.remove(0);
        active.add(object);
        return object;
    }

    public void release(T pooledObject) {
        System.err.println("Releasing object " + pooledObject + " from pool.");
        active.remove(pooledObject);
        reset(pooledObject);
        pool.add(pooledObject);
    }

    protected abstract T create(Object... args);
    protected abstract void reset(T pooledObject);
}
