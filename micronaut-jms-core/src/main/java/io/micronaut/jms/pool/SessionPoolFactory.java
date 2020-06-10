package io.micronaut.jms.pool;

import io.micronaut.context.annotation.Context;

import javax.jms.Connection;

@Context
public class SessionPoolFactory {
    public SessionPool getSessionPool(Connection connection) {
        return new SessionPool(10, 20, connection);
    }
}
