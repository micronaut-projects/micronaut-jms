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

import javax.jms.JMSException;

public abstract class PooledObject<T> implements AutoCloseable {

    private final AbstractPool<PooledObject<T>> pool;
    private final T object;

    protected PooledObject(AbstractPool<PooledObject<T>> pool,
                           T object) {
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
