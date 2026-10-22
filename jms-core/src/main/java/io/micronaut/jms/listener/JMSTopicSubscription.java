/*
 * Copyright 2017-2026 original authors
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

import org.jspecify.annotations.Nullable;

/**
 * A named subscription to a topic: durable, shared, or both.
 *
 * <p>A durable subscription keeps messages published while its listener is not connected. A shared
 * subscription spreads messages over all listeners using the same subscription name (JMS 2.0). An unshared
 * durable subscription is identified by its name and the client ID of its connection, so it gets a
 * connection of its own with that client ID; so does any subscription given an explicit client ID.</p>
 *
 * @param name     the subscription name
 * @param durable  whether the subscription is durable
 * @param shared   whether the subscription is shared between listeners
 * @param clientId the client ID of the listener's connection, or {@code null} for the default
 * @since 5.2.0
 */
public record JMSTopicSubscription(String name, boolean durable, boolean shared, @Nullable String clientId) {

    /**
     * @throws IllegalArgumentException if the name is empty
     */
    public JMSTopicSubscription {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("A topic subscription requires a name");
        }
    }

    /**
     * @return whether the listener needs a connection of its own, with {@link #effectiveClientId()} as its
     * client ID: for an unshared durable subscription, or when a client ID is given
     */
    public boolean requiresDedicatedConnection() {
        return clientId != null || (durable && !shared);
    }

    /**
     * @return the client ID for a dedicated connection: the given client ID, or the subscription name
     */
    public String effectiveClientId() {
        return clientId != null ? clientId : name;
    }
}
