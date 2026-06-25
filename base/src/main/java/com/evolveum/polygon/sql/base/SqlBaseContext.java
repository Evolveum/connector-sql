/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.sql.base.schema.SqlSchema;
import com.evolveum.polygon.sql.base.connection.HikariConnectionPool;
import com.evolveum.polygon.sql.base.connection.SqlConnection;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Context for SQL connector operations.
 * Manages connection pool, schema, and handlers.
 *
 * <p>All public methods are safe to call concurrently after initialization.</p>
 */
public class SqlBaseContext {

    private static final String POOL_CLOSED_MSG = "Connection pool has been closed";

    private final SqlConnectorConfiguration configuration;
    private SqlSchema schema;
    private Map<ObjectClass, ObjectClassHandler> handlers;
    private volatile HikariConnectionPool connectionPool;

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
        return handlers != null ? Collections.unmodifiableMap(handlers) : Collections.emptyMap();
    }

    public void handlers(Map<ObjectClass, ObjectClassHandler> handlers) {
        if (handlers == null) {
            this.handlers = null;
            return;
        }
        this.handlers = Map.copyOf(handlers);
    }

    public ObjectClassHandler handlerFor(ObjectClass objectClass) {
        return handlers != null ? handlers.get(objectClass) : null;
    }

    HikariConnectionPool getConnectionPool() {
        return connectionPool;
    }

    /**
     * Initializes or reinitializes the connection pool, properly closing any previous pool.
     */
    public void initializeConnectionPool() {
        HikariConnectionPool oldPool;
        synchronized (this) {
            if (connectionPool != null) {
                oldPool = connectionPool;
            } else {
                oldPool = null;
            }
            connectionPool = new HikariConnectionPool(configuration);
            connectionPool.initialize();
        }

        if (oldPool != null) {
            oldPool.close();
        }
    }

    public void testConnection() throws Exception {
        HikariConnectionPool pool = connectionPool;
        if (pool == null) {
            throw new IllegalStateException("Connection pool not initialized");
        }
        pool.test();
    }

    /**
     * Closes the connection pool and releases all resources.
     * After calling this method, call {@link #initializeConnectionPool()} to reconnect.
     */
    public void close() {
        synchronized (this) {
            if (connectionPool != null) {
                connectionPool.close();
                connectionPool = null;
            }
        }
    }

    /**
     * Gets a SqlConnection from the pool.
     * @return a connection wrapper
     * @throws IllegalStateException if pool is not initialized or closed
     */
    public SqlConnection getConnection() {
        HikariConnectionPool pool = connectionPool;
        if (pool == null) {
            throw new IllegalStateException("Connection pool not initialized");
        }
        try {
            return pool.getConnection();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to get connection from pool", e);
        }
    }
}