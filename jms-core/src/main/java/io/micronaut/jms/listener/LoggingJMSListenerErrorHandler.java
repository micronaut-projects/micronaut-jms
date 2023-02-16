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

import javax.jms.Message;
import javax.jms.Session;

/**
 * Default logger for all {@link JMSListener}s. Logs any exception that occurs while receiving, processing, or handling
 *  the message
 *
 * @author Elliott Pope
 * @since 2.1.1
 */
public class LoggingJMSListenerErrorHandler implements JMSListenerErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingJMSListenerErrorHandler.class);
    
    @Override
    public void handle(Session session, Message message, Throwable ex) {
        LOGGER.error("Failed to handle message receive", ex);
    }
}
