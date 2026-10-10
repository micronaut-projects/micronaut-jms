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
package io.micronaut.jms.dev;

import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;
import java.util.Map;

/**
 * One ActiveMQ Artemis broker for the tests of the module.
 */
final class Artemis {

    private static ArtemisContainer container;

    private Artemis() {
    }

    static synchronized Map<String, String> properties() {
        if (container == null) {
            container = new ArtemisContainer("apache/activemq-artemis:latest")
                .withUser("artemis")
                .withPassword("artemis");
            container.waitingFor(Wait.forLogMessage(".*AMQ221007: Server is now active.*\\n", 1)
                .withStartupTimeout(Duration.ofSeconds(120)));
            container.start();
        }
        return Map.of(
            "micronaut.jms.activemq.artemis.enabled", "true",
            "micronaut.jms.activemq.artemis.connection-string", container.getBrokerUrl(),
            "micronaut.jms.activemq.artemis.username", "artemis",
            "micronaut.jms.activemq.artemis.password", "artemis"
        );
    }
}
