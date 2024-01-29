/*
 * Copyright 2017-2022 original authors
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;

/**
 * Attempts to rollback a transaction on the given {@link Session}. If it fails then the exception is logged.
 *
 * @author Elliott Pope
 * @since 3.0.0
 */
public class TransactionalJMSListenerErrorHandler implements JMSListenerErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionalJMSListenerErrorHandler.class);

    @Override
    public void handle(Session session, Message message, Throwable ex) {
        LOGGER.debug("Attempting to rollback transaction on session {} for message {} due to {}", session, message, ex);
        try {
            session.rollback();
            LOGGER.debug("Successfully rolled back transaction on session {} for message {}", session, message);
        } catch (JMSException e) {
            LOGGER.error("Failed to rollback transaction on session: " + e.getMessage(), e);
        }
    }

    @Override
    public int getOrder() {
        return Integer.valueOf(-200);
    }
}
