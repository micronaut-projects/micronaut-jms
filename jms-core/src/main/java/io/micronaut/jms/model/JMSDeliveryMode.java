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
package io.micronaut.jms.model;

/***
 * Enumeration of the different persistence modes of JMS Brokers.
 *
 * @author elliottpope
 * @since 1.0
 *
 * @see javax.jms.DeliveryMode
 */
public enum JMSDeliveryMode {
    /***
     * This mode does not require the broker to log the message to stable storage.
     *
     * @see javax.jms.DeliveryMode#NON_PERSISTENT
     */
    NON_PERSISTENT(1),

    /***
     * This mode does requires the broker to log the message to stable storage.
     *
     * @see javax.jms.DeliveryMode#PERSISTENT
     */
    PERSISTENT(2);

    private Integer value;

    JMSDeliveryMode(Integer value) {
        this.value = value;
    }

    public static JMSDeliveryMode from(Integer value) {
        for (JMSDeliveryMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("No JMSDeliveryMode defined for " + value);
    }
}
