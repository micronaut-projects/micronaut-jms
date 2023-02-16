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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

/**
 * Commits a JMS transaction upon successful handling of a message.
 *
 * @author Elliott Pope
 * @since 3.0.0
 */
public class TransactionalJMSListenerSuccessHandler implements JMSListenerSuccessHandler {

    public static final int POSITION = 200;

    @Override
    public void handle(Session session, Message message) throws JMSException {
        session.commit();
    }

    @Override
    public int getOrder() {
        return POSITION;
    }
}
