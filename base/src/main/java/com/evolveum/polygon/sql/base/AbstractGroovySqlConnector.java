/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.dev.ConnDevObjectClass;
import com.evolveum.polygon.conndev.dev.ConnDevSchema;
import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.sql.base.dev.SqlObjectClassDevHandler;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerBuilder;
import com.evolveum.polygon.sql.base.objectclass.SqlObjectClassMapper;
import com.evolveum.polygon.sql.base.schema.SqlQuerydslMetadataFactory;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator;
import com.evolveum.polygon.sql.base.search.SqlSearchOperation;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.spi.Configuration;

import java.sql.SQLException;
import java.util.List;

/**
 * Base connector class for SQL database connectors.
 * Extends ClassHandlerConnectorBase to support separate handlers per object class.
 *
 * <p>This class manages its lifecycle. Operations that require a pool will
 * lazily initialize it on first call or reinitialize on each call as configured.</p>
 */
public abstract class AbstractGroovySqlConnector<T extends SqlConnectorConfiguration>
        extends ClassHandlerConnectorBase {

    private final boolean reinitializeOnEachCall;
    private boolean initialized;
    private SqlBaseContext context;
    private java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean(false);

    @Deprecated
    protected AbstractGroovySqlConnector() {
        this(true);
    }

    protected AbstractGroovySqlConnector(boolean reinitializeOnEachCall) {
        this.reinitializeOnEachCall = reinitializeOnEachCall;
    }

    @Override
    public SqlConnectorConfiguration getConfiguration() {
        checkInitialized();
        return context.configuration();
    }

    @Override
    public SqlBaseContext context() {
        initialize();
        return context;
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
        synchronized (this) {
            if (closed.get()) {
                throw new IllegalStateException("Connector has been disposed and cannot be re-initialized");
            }
            if (cfg instanceof SqlConnectorConfiguration sqlConf) {
                context = new SqlBaseContext(sqlConf);
                initialized = false;
            } else {
                throw new IllegalArgumentException("Configuration must be an instance of SqlConnectorConfiguration");
            }
        }
    }

    private void initialize() {
        if (closed.get()) {
            return;
        }
        synchronized (this) {
            if (reinitializeOnEachCall || !initialized) {
                initialize0();
                initialized = true;
            }
        }
    }

    private void initialize0() {
        // Initialize connection pool first (properly closes old pool if reinitializing) — schema
        // detection needs a live connection.
        context.initializeConnectionPool();

        SqlQuerydslMetadataFactory metadataFactory = null;

        // Auto-discover schema if enabled: detect raw JDBC metadata, then translate it into the one
        // framework schema model (conndev BaseSchema); everything else derives from that model.
        if (context.configuration().isAutoDiscoverSchema()) {
            try {
                var detector = new SqlSchemaDetector(context);
                var tables = detector.discover();
                var templates = detector.getSQLTemplates();
                // For H2, ensure we use H2-compatible templates which handle quoted identifiers correctly
                if (templates == null) {
                    try (var conn = context.getConnection()) {
                        var dbProductName = conn.getConnection().getMetaData().getDatabaseProductName();
                        if (dbProductName.toUpperCase().contains("H2")) {
                            templates = new com.querydsl.sql.H2Templates();
                        }
                    } catch (SQLException e) {
                        // Use default if we can't detect the database
                    }
                }
                if (templates == null) {
                    templates = com.querydsl.sql.SQLTemplates.DEFAULT;
                }
                metadataFactory = new SqlQuerydslMetadataFactory(tables, templates);
                context.setMetadataFactory(metadataFactory);
                context.setSqlQueryEngine(metadataFactory.getQueryEngine());

                // In development mode the shared conndev_ObjectClass / conndev_Attribute classes are
                // part of the schema, so midPoint can search the discovered schema.
                var additional = Boolean.TRUE.equals(context.configuration().isDevelopmentMode())
                        ? ConnDevSchema.objectClassInfos() : List.<ObjectClassInfo>of();

                context.schema(new SqlSchemaTranslator(tables).translate(getClass(), context, additional));

                // Build SQL <-> ConnId object class mappings for QueryDSL search
                var objectClassMappings = SqlObjectClassMapper.buildAll(context.schema(), metadataFactory);
                context.setObjectClassMappings(objectClassMappings);
            } catch (SQLException e) {
                throw new ConnectionFailedException("Schema detection failed: " + e.getMessage(), e);
            }
        }

        // Initialize handlers
        var handlerBuilder = new SqlHandlerBuilder(context);
        initializeObjectClassHandler(handlerBuilder);
        if (Boolean.TRUE.equals(context.configuration().isDevelopmentMode())) {
            handlerBuilder.register(new ObjectClass(ConnDevObjectClass.OBJECT_CLASS_NAME),
                    ObjectSearchOperation.class, new SqlObjectClassDevHandler(context));
        }

        // Register QueryDSL-based search operation for all application object classes (tables)
        if (context.getObjectClassMappings() != null) {
            for (var entry : context.getObjectClassMappings().entrySet()) {
                var objectClass = entry.getKey();
                handlerBuilder.register(objectClass, ObjectSearchOperation.class,
                        new SqlSearchOperation(context, metadataFactory, objectClass));
            }
        }

        context.handlers(handlerBuilder.build());
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
        } catch (ConnectionFailedException | InvalidCredentialException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectionFailedException("Connection test failed: " + e.getMessage());
        }
    }

    public Schema schema() {
        initialize();
        return context.schema().connIdSchema();
    }

    @Override
    public void dispose() {
        if (closed.compareAndSet(false, true)) {
            if (context != null) {
                context.close();
            }
            context = null;
        }
    }

    private void checkInitialized() {
        if (context == null || closed.get()) {
            throw new IllegalStateException("Connector not initialized. Call init() first.");
        }
    }
}