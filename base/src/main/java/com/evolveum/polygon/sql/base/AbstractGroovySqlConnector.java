/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.groovy.SqlHandlerBuilder;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.spi.Configuration;

/**
 * Base connector class for SQL database connectors.
 * Extends ClassHandlerConnectorBase to support separate handlers per object class.
 */
public abstract class AbstractGroovySqlConnector<T extends SqlConnectorConfiguration> 
        extends ClassHandlerConnectorBase {

    private final boolean reinitializeOnEachCall;

    private boolean initialized;
    private SqlBaseContext context;

    @Deprecated
    protected AbstractGroovySqlConnector() {
        this(true);
    }

    protected AbstractGroovySqlConnector(boolean reinitializeOnEachCall) {
        this.reinitializeOnEachCall = reinitializeOnEachCall;
    }

    @Override
    public SqlConnectorConfiguration getConfiguration() {
        return context.configuration();
    }

    @Override
    public ObjectClassHandler handlerFor(ObjectClass objectClass) throws UnsupportedOperationException {
        initialize();
        var handler = context.handlerFor(objectClass);
        if (handler == null) {
            throw new UnsupportedOperationException("Cannot find handler for " + objectClass);
        }
        return handler;
    }

    @Override
    public void init(Configuration cfg) {
        if (cfg instanceof SqlConnectorConfiguration sqlConf) {
            context = new SqlBaseContext(sqlConf);
        } else {
            throw new IllegalArgumentException("Configuration must be an instance of SqlConnectorConfiguration");
        }
    }

    private void initialize() {
        if (reinitializeOnEachCall || !initialized) {
            initialize0();
            initialized = true;
        }
    }

    private void initialize0() {
        // Auto-discover schema if enabled
        if (context.configuration().isAutoDiscoverSchema()) {
            var schemaDetector = new SqlSchemaDetector(context);
            schemaDetector.discover();
        }

        // Initialize handlers
        var handlerBuilder = new SqlHandlerBuilder(context);
        initializeObjectClassHandler(handlerBuilder);
        context.handlers(handlerBuilder.build());

        // Initialize connection pool
        context.initializeConnectionPool();
    }

    /**
     * Creates initial configuration for Abstract Groovy SQL Connector
     *
     * @param builder handler builder for object class handlers
     */
    protected abstract void initializeObjectClassHandler(SqlHandlerBuilder builder);

    public void test() {
        initialize();
        try {
            context.testConnection();
        } catch (Exception e) {
            if (e instanceof ConnectionFailedException) {
                throw (ConnectionFailedException) e;
            }
            if (e instanceof InvalidCredentialException) {
                throw (InvalidCredentialException) e;
            }
            throw new ConnectionFailedException("Connection test failed: " + e.getMessage(), e);
        }
    }

    public Schema schema() {
        initialize();
        return context.schema().connIdSchema();
    }

    @Override
    public void dispose() {
        if (context != null) {
            context.close();
        }
    }
}
