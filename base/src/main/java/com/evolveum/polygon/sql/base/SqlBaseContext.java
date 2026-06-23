/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.schema.SqlSchema;
import com.evolveum.polygon.sql.base.connection.SqlConnection;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Map;

/**
 * Context for SQL connector operations.
 * Manages connection pool, schema, and handlers.
 */
public class SqlBaseContext {

    private final SqlConnectorConfiguration configuration;
    private SqlSchema schema;
    private Map<ObjectClass, ObjectClassHandler> handlers;

    public SqlBaseContext(SqlConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    public SqlConnectorConfiguration configuration() {
        return configuration;
    }

    public SqlSchema schema() {
        return schema;
    }

    public void schema(SqlSchema schema) {
        this.schema = schema;
    }

    public Map<ObjectClass, ObjectClassHandler> handlers() {
        return handlers;
    }

    public void handlers(Map<ObjectClass, ObjectClassHandler> handlers) {
        this.handlers = handlers;
    }

    public ObjectClassHandler handlerFor(ObjectClass objectClass) {
        return handlers != null ? handlers.get(objectClass) : null;
    }

    public void initializeConnectionPool() {
        // Initialize HikariCP connection pool

    }

    public void testConnection() throws Exception {
        // Test database connection
    }

    public void close() {
        // Close connection pool
    }

    public SqlConnection getConnection() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
