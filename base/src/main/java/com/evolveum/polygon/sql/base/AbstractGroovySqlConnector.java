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
import com.evolveum.polygon.conndev.spi.ObjectSyncOperation;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.dev.SqlObjectClassDevHandler;
import com.evolveum.polygon.sql.base.groovy.SqlGroovySchemaLoader;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerBuilder;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator;
import com.evolveum.polygon.sql.base.search.SqlSearchOperation;
import com.evolveum.polygon.sql.base.sync.SqlSyncOperation;
import com.evolveum.polygon.sql.base.sync.SyncConfig;
import com.querydsl.sql.SQLTemplates;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.PoolableConnector;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base connector class for SQL database connectors.
 * Extends ClassHandlerConnectorBase to support separate handlers per object class.
 *
 * <p>This class manages its lifecycle. Operations that require a pool will
 * lazily initialize it on first call or reinitialize on each call as configured.</p>
 */
public abstract class AbstractGroovySqlConnector<T extends SqlConnectorConfiguration>
        extends ClassHandlerConnectorBase implements PoolableConnector {

    private final boolean reinitializeOnEachCall;
    private boolean initialized;
    private SqlBaseContext context;
    private AtomicBoolean closed = new AtomicBoolean(false);

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


        var builder = new SqlSchemaBuilderImpl(getClass(), context);
        var groovyContext = context.configuration().groovyContext();

        // Load Groovy scripts into the builder via subclass-provided init method
        var loader = new SqlGroovySchemaLoader(context, builder, groovyContext);
        initializeSchema(builder);
        initializeSchema(loader);

        try {
            var detector = new SqlSchemaDetector(context);
            var templates = detector.getSQLTemplates();
            if (templates == null) {
                templates = SQLTemplates.DEFAULT;
            }
            context.setSqlTemplates(templates);

            var additional = new ArrayList<ObjectClassInfo>();
            if (Boolean.TRUE.equals(context.configuration().getDevelopmentMode())) {
                additional.addAll(ConnDevSchema.objectClassInfos());
                additional.add(sqlObjectClassBlock());
            }

            if (Boolean.TRUE.equals(context.configuration().getAutoDiscoverSchema())) {
                try {
                    var tables = detector.discover();
                    // In development mode the shared conndev_ObjectClass / conndev_Attribute classes are
                    // part of the schema, so midPoint can search the discovered schema.


                    context.schema(new SqlSchemaTranslator(builder, tables)
                            .connector(getClass(), context)
                            .translate(additional));
                } catch (SQLException e) {
                    throw new ConnectionFailedException("Schema detection failed: " + e.getMessage(), e);
                }
            } else {
                additional.forEach(builder::defineObjectClass);
                context.schema(builder.build());
            }

        } catch (SQLException ex) {
            throw new ConnectionFailedException(ex.getMessage(), ex);
        }


        // Auto-discover schema if enabled: detect raw JDBC metadata, then translate it into the one
        // framework schema model (conndev BaseSchema); everything else derives from that model.


        // Initialize handlers
        var handlerBuilder = new SqlHandlerBuilder(context);
        initializeObjectClassHandler(handlerBuilder);

        if (Boolean.TRUE.equals(context.configuration().getDevelopmentMode())) {
            handlerBuilder.register(new ObjectClass(ConnDevObjectClass.OBJECT_CLASS_NAME),
                    ObjectSearchOperation.class, new SqlObjectClassDevHandler(context));
        }

        // Register QueryDSL-based search and sync operations for all application object classes (tables)
        if (context.schema() != null) {
            for (SqlObjectClassDefinition def : context.schema().objectClasses()) {
                var oc = def.objectClass();
                var mapping = def.sql();
                if (mapping != null) {
                    handlerBuilder.register(oc, ObjectSearchOperation.class, new SqlSearchOperation(context, def));
                    handlerBuilder.register(oc, ObjectSyncOperation.class,
                        new SqlSyncOperation(context, def, SyncConfig.defaultFor(def)));
                }
            }
        }

        context.handlers(handlerBuilder.build());
    }

    private static final String SQL_BLOCK = "sql";
    private static final String SQL_BLOCK_TYPE = ConnDevObjectClass.protocolBlockType(SQL_BLOCK);

    /** The object-class-level {@code sql} block: DB schema and table name. */
    private static ObjectClassInfo sqlObjectClassBlock() {
        var builder = new ObjectClassInfoBuilder();
        builder.setType(SQL_BLOCK_TYPE);
        builder.setEmbedded(true);
        builder.addAttributeInfo(AttributeInfoBuilder.build("table", String.class));
        builder.addAttributeInfo(AttributeInfoBuilder.build("schema", String.class));
        return builder.build();
    }

    protected void initializeSchema(SqlSchemaBuilder builder) {
        // NOOP for overriding
    }

    /**
     * Initializes schema by loading Groovy scripts into the provided loader.
     *
     * @param loader the Groovy schema loader to populate
     */
    protected abstract void initializeSchema(SqlGroovySchemaLoader loader);

    /**
     * Initializes operation handlers for object classes by loading Groovy scripts.
     *
     * @param builder the handler builder to populate
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

    @Override
    public void checkAlive() {
        if (closed.get()) {
            throw new IllegalStateException("Connector was closed.");
        }
    }
}
