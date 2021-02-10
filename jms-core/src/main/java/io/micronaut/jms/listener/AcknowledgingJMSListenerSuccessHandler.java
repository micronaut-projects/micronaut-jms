/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.messaging.exceptions.MessageAcknowledgementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

/***
 *
 * Success handler to acknowledge that the listener has received and processed the message successfully.
 *  Handler should be enabled if the listener uses the {@link Session#CLIENT_ACKNOWLEDGE} mode.
 *
 * @author Elliott Pope
 * @since 1.0.0.M2
 */
public class AcknowledgingJMSListenerSuccessHandler implements JMSListenerSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(AcknowledgingJMSListenerSuccessHandler.class);

    @Override
    public void handle(Session session, Message message) throws JMSException {
        try {
            message.acknowledge();
        } catch (JMSException e) {
            logger.error("Failed to acknowledge receipt of message with the broker. " +
                    "This message may be falsely retried.", e);
            throw new MessageAcknowledgementException(e.getMessage(), e);
        }
    }
}
