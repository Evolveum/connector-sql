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
import com.evolveum.polygon.conndev.spi.CompositeObjectClassHandler;
import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.dev.SqlObjectClassDevHandler;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import com.evolveum.polygon.sql.base.groovy.impl.SqlOperationSupportBuilderImpl;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.schema.TableFilter;
import com.querydsl.sql.SQLTemplates;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.PoolableConnector;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
    private boolean connected;
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
        ensureConnectionInitialized();
        return context;
    }

    @Override
    public ObjectClassHandler handlerFor(ObjectClass objectClass) throws UnsupportedOperationException {
        ensureConnectionInitialized();
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
                connected = false;
            } else {
                throw new IllegalArgumentException("Configuration must be an instance of SqlConnectorConfiguration");
            }
        }
    }

    /**
     * Builds the schema, connecting to the database and discovering it (as {@code test()} and actual
     * data operations do) whenever the required connection parameters are present. Only falls back to
     * building the schema from local Groovy/YAML definitions alone when the configuration is still
     * incomplete (e.g. a brand new, not yet filled in wizard form) — connecting is pointless then and
     * would only fail. Does not undo a richer, DB-discovered schema already produced by a prior
     * {@link #ensureConnectionInitialized()} on this instance.
     */
    private void ensureSchemaInitialized() {
        if (closed.get()) {
            return;
        }
        synchronized (this) {
            if (reinitializeOnEachCall || !initialized) {
                boolean allowConnection = context.configuration().isComplete();
                initialize0(allowConnection);
                initialized = true;
                connected = allowConnection;
            }
        }
    }

    /** Ensures a live connection pool exists and, if enabled, the schema has been discovered from the database. */
    private void ensureConnectionInitialized() {
        if (closed.get()) {
            return;
        }
        synchronized (this) {
            if (reinitializeOnEachCall || !connected) {
                initialize0(true);
                initialized = true;
                connected = true;
            }
        }
    }

    private void initialize0(boolean allowConnection) {
        if (allowConnection) {
            // Properly closes the old pool first if reinitializing — schema detection needs a live connection.
            context.initializeConnectionPool();
        }

        var builder = new SqlSchemaBuilderImpl(getClass(), context);
        var groovyContext = context.configuration().groovyContext();

        // Load Groovy/YAML scripts into the builder via subclass-provided init method
        var loader = new SqlSchemaDefinitionLoader(builder, groovyContext);
        initializeSchema(builder);
        initializeSchema(loader);

        SqlSchemaDetector detector;
        try {
            detector = new SqlSchemaDetector(context);
        } catch (SQLException ex) {
            throw new ConnectionFailedException(ex.getMessage(), ex);
        }

        var tableFilter = new TableFilter(
                Boolean.TRUE.equals(context.configuration().getScanTables()),
                Boolean.TRUE.equals(context.configuration().getScanViews()),
                context.configuration().getScanTableFilter(),
                context.configuration().getScanViewFilter(),
                context.configuration().getScanExcludeTables(),
                context.configuration().getScanExcludeViews()
        );
        detector.setTableFilter(tableFilter);

        var templates = detector.getSQLTemplates();
        if (templates == null) {
            templates = SQLTemplates.DEFAULT;
        }
        context.setSqlTemplates(templates);

        var additional = new ArrayList<ObjectClassInfo>();
        if (Boolean.TRUE.equals(context.configuration().getDevelopmentMode())) {
            additional.addAll(ConnDevSchema.objectClassInfos(
                    List.of(ConnDevSchema.embeddedBlock(SQL_BLOCK, SQL_BLOCK_TYPE)), List.of()));
            additional.add(sqlObjectClassBlock());
        }

        List<SqlTableInfo> tables;
        try {
            if (tableFilter.isDiscoveryEnabled()) {
                tables = detector.discover();
            } else if (!builder.tableRefs().isEmpty()) {
                tables = detector.discover(builder.tableRefs());

            } else {
                tables = new ArrayList<>();
            }
        } catch (SQLException e) {
            throw new ConnectionFailedException("Schema detection failed: " + e.getMessage(), e);

        }

        // Translate into framework schema model; translator collects detected actions for handlers.
        var translator = new SqlSchemaTranslator(builder, tables);
        context.schema(translator
                .connector(getClass(), context)
                .translate(additional));
        // Initialize handlers

        var handlerBuilder = new SqlOperationSupportBuilderImpl(context);

        var handlerLoader = new SqlHandlerLoader(context, handlerBuilder);
        initializeObjectClassHandler(handlerLoader);


        // Trigger defaults for each and every object class, then apply detected handler effects
        if (context.schema() != null) {
            var detectedActions = translator.getDetectedActions();
            for (SqlObjectClassDefinition def : context.schema().objectClasses()) {
                handlerBuilder.objectClass(def.name());
                var actions = detectedActions.get(def.name());
                if (actions != null) {
                    var ocHandlerBuilder = handlerBuilder.objectClass(def.name());
                    for (var action : actions) {
                        action.applyToHandlers(ocHandlerBuilder);
                    }
                }
            }
        }

        var handlers = handlerBuilder.build();
        // FIXME: This should be somehow unified
        if (Boolean.TRUE.equals(context.configuration().getDevelopmentMode())) {
            var name = new ObjectClass(ConnDevObjectClass.OBJECT_CLASS_NAME);
            var handler = CompositeObjectClassHandler.of(name,ObjectSearchOperation.class, new SqlObjectClassDevHandler(context));
            handlers.put(name, handler);
        }

        context.handlers(handlers);
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
    protected abstract void initializeSchema(SqlSchemaDefinitionLoader loader);

    /**
     * Initializes operation handlers for object classes by loading Groovy scripts.
     *
     * @param builder the handler builder to populate
     */
    protected abstract void initializeObjectClassHandler(SqlHandlerLoader builder);

    public void test() {
        ensureConnectionInitialized();
        try {
            context.testConnection();
        } catch (ConnectionFailedException | InvalidCredentialException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectionFailedException("Connection test failed: " + e.getMessage());
        }
    }

    public Schema schema() {
        ensureSchemaInitialized();
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
