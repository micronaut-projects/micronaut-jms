package io.micronaut.jms.activemq;

import io.micronaut.jms.AbstractJMSTest;
import io.micronaut.test.annotation.MicronautTest;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.ConnectionFactory;

@MicronautTest
public class ActiveMQJMSTest extends AbstractJMSTest {
    @Override
    protected ConnectionFactory getConnectionFactory() {
        return new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
    }
}
