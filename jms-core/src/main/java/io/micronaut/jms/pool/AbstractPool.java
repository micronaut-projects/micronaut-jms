/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jms.pool;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public abstract class AbstractPool<T extends PooledObject<?>> {

    protected final List<T> pool = Collections.synchronizedList(new LinkedList<>());
    protected final int initialSize;
    protected final int maxSize;

    private final List<T> active = Collections.synchronizedList(new LinkedList<>());

    protected AbstractPool(int initialSize,
                           int maxSize) {
        this.initialSize = initialSize;
        this.maxSize = maxSize;
    }

    /***
     *
     * Requests an object {@param <T>} from the pool. If the pool is empty then a new object is added to the pool.
     *      If the number of active connections exceeds the configured size then an {@link RuntimeException} is thrown
     *
     * @param args - the arguments to pass to the create method, or to help select an object from the pool.
     * @return a {@link PooledObject} from the pool.
     */
    public T request(Object... args) {
        if (pool.isEmpty()) {
            if (active.size() >= maxSize) {
                throw new IllegalStateException("Maximum pool size reached");
            }
            pool.add(create(args));
        }
        T object = pool.remove(0);
        active.add(object);
        return object;
    }

    /***
     * Release the provided object and return it to the pool.
     *
     * @param pooledObject - the object to return to the pool
     */
    public void release(T pooledObject) {
        active.remove(pooledObject);
        reset(pooledObject);
        pool.add(pooledObject);
    }

    /***
     * Create an object for the pool.
     *
     * @param args - the arguments to be provided to the create method.
     * @return a new object of type {@param <T>} for the pool.
     */
    protected abstract T create(Object... args);

    /***
     * Reset the provided object so that it can be returned to the pool ready for reuse.
     *
     * @param pooledObject
     */
    protected abstract void reset(T pooledObject);
}
