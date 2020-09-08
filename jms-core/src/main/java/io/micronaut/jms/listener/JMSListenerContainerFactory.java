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
package io.micronaut.jms.listener;

import io.micronaut.jms.model.JMSDestinationType;
import io.micronaut.jms.pool.JMSConnectionPool;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import javax.jms.MessageListener;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/***
 *
 * Factory for generating and tracking {@link JMSListenerContainer} within the
 *      Micronaut Bean Context.
 *
 * @see JMSListenerContainer
 *
 * @author elliott
 */
@Singleton
public class JMSListenerContainerFactory {

    private final Map<String, JMSListenerContainer<?>> listeners = new ConcurrentHashMap<>();

    /***
     *
     * Generates a new {@link JMSListenerContainer} and registers the
     *      provided {@link MessageHandler} as the receiving executable method.
     *
     * NOTE: this method is not recommended for registering new {@link JMSListenerContainer}s
     *      however can be used to register them dynamically and allow for safe shutdown
     *      within the Bean Context.
     *
     * @param connectionPool
     * @param destination
     * @param handler
     * @param clazz
     * @param type
     * @param <T>
     */
    public <T> void getJMSListener(
            final JMSConnectionPool connectionPool,
            final String destination,
            final MessageHandler<T> handler,
            final Class<T> clazz,
            final JMSDestinationType type) {
        final JMSListenerContainer<T> listener = new JMSListenerContainer<>(
                connectionPool,
                type);
        listener.setThreadPoolSize(1);
        listener.registerListener(destination, handler, clazz);
        listeners.put(destination, listener);
    }

    /***
     *
     * Generates a new {@link JMSListenerContainer} and registers the
     *      provided {@link MessageListener} as the receiving executable method.
     *
     * NOTE: this method is not recommended for registering new {@link JMSListenerContainer}s
     *      however can be used to register them dynamically and allow for safe shutdown
     *      within the Bean Context.
     *
     * @param connectionPool
     * @param destination
     * @param listener
     * @param clazz
     * @param transacted
     * @param acknowledgment
     * @param type
     * @param <T>
     */
    public <T> void getJMSListener(
            final JMSConnectionPool connectionPool,
            final String destination,
            final MessageListener listener,
            final Class<T> clazz,
            final boolean transacted,
            final int acknowledgment,
            final JMSDestinationType type) {
        final JMSListenerContainer<T> container = new JMSListenerContainer<>(
                connectionPool,
                type);
        container.setThreadPoolSize(1);
        container.registerListener(destination, listener, clazz, transacted, acknowledgment);
        listeners.put(destination, container);
    }

    /***
     *
     * @param destination
     * @return the {@link JMSListenerContainer} that is listening to the given {@param destination}
     */
    public JMSListenerContainer<?> getRegisteredListener(String destination) {
        return listeners.get(destination);
    }

    /***
     * Shuts down all the listeners registered with this factory instance.
     */
    @PreDestroy
    public void shutdown() {
        listeners.forEach((destination, listener) -> {
            listener.shutdown();
        });
    }
}
