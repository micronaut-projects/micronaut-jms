package io.micronaut.jms.model;

public enum JMSDeliveryMode {
    NON_PERSISTENT(1),
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
