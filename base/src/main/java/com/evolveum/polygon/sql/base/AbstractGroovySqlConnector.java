/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.dev.ConnDevAttribute;
import com.evolveum.polygon.conndev.dev.ConnDevObjectClass;
import com.evolveum.polygon.conndev.dev.ConnDevSchema;
import com.evolveum.polygon.conndev.groovy.GroovyScriptValidator;
import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.conndev.groovy.ScriptValidationResult;
import com.evolveum.polygon.conndev.spi.ClassHandlerConnectorBase;
import com.evolveum.polygon.conndev.spi.CompositeObjectClassHandler;
import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import com.evolveum.polygon.conndev.yaml.decl.GroovySyntaxChecker;
import com.evolveum.polygon.conndev.yaml.decl.LocatedDocument;
import com.evolveum.polygon.conndev.yaml.decl.YamlScriptValidator;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectOperationSupportBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.dev.SqlDevelopmentMode;
import com.evolveum.polygon.sql.base.dev.SqlObjectClassDevHandler;
import com.evolveum.polygon.sql.base.dev.SqlTableDevHandler;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import com.evolveum.polygon.sql.base.groovy.impl.SqlOperationSupportBuilderImpl;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.schema.TableFilter;
import com.evolveum.polygon.sql.base.yaml.YamlSqlOperationsLoader;
import com.querydsl.sql.SQLTemplates;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.PoolableConnector;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base connector class for SQL database connectors.
 * Extends ClassHandlerConnectorBase to support separate handlers per object class.
 *
 * <p>This class manages its lifecycle. Operations that require a pool will
 * lazily initialize it on first call or reinitialize on each call as configured.</p>
 */
public abstract class AbstractGroovySqlConnector
        extends ClassHandlerConnectorBase<SqlBaseContext> implements PoolableConnector {

    private static final String SQL_BLOCK = "sql";
    private static final String SQL_BLOCK_TYPE = ConnDevObjectClass.protocolBlockType(SQL_BLOCK);
    private static final String SQL_ATTRIBUTE_BLOCK_TYPE = ConnDevAttribute.attributeProtocolBlockType(SQL_BLOCK);

    private AtomicBoolean closed = new AtomicBoolean(false);

    protected AbstractGroovySqlConnector(boolean reinitializeOnEachCall) {
        super(reinitializeOnEachCall);
    }

    @Override
    public SqlConnectorConfiguration getConfiguration() {
        checkInitialized();
        return context.configuration();
    }

    @Override
    public SqlBaseContext context() {
        initializeHandlers();
        return context;
    }

    @Override
    public ObjectClassHandler handlerFor(ObjectClass objectClass) throws UnsupportedOperationException {
        initializeHandlers();
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
                coreInitialized = false;
                fullyInitialized = false;
            } else {
                throw new IllegalArgumentException("Configuration must be an instance of SqlConnectorConfiguration");
            }
        }
    }

    @Override
    public void test() {
        initializeHandlers();
        try {
            context.testConnection();
        } catch (ConnectionFailedException | InvalidCredentialException e) {
            throw e;
        } catch (Exception e) {
            throw new ConnectionFailedException("Connection test failed: " + e.getMessage());
        }
    }

    @Override
    public Schema schema() {
        initializeCore();
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

    @Override
    public void checkAlive() {
        if (closed.get()) {
            throw new IllegalStateException("Connector was closed.");
        }
    }

    protected void initializeSchema(SqlSchemaBuilder builder) {
        // NOOP for overriding
    }

    /**
     * Validates the candidate script against a throwaway target seeded with all currently
     * deployed sibling scripts (via {@link #schemaResources} / {@link #operationResources}, minus
     * {@code filename} itself), so cross-references to them (e.g. a schema attribute's {@code
     * referencedObjectClass}) resolve during evaluation and build, and so the candidate replaces
     * rather than merges with its own old content.
     */
    @Override
    protected ScriptValidationResult validateScript(ScriptValidationRequest request) throws Exception {
        initializeCore();
        if (ScriptValidationRequest.ARTIFACT_KIND_SCHEMA.equals(request.artifactKind())) {
            if (request.isYaml()) {
                return validateYamlSchema(request);
            }
            var builder = new SqlSchemaBuilderImpl(getClass(), context);
            var loader = new SqlSchemaDefinitionLoader(builder, context.configuration().groovyContext());
            schemaResources(request.filename()).forEach(loader::loadFromResource);
            return GroovyScriptValidator.validate(loader::parse, () -> {
                builder.applyStructuralRules();
                builder.build();
            }, request.scriptText(), request.operation());
        }
        if (request.isYaml()) {
            return validateYamlOperations(request);
        }
        var handlerBuilder = new SqlOperationSupportBuilderImpl(context);
        var handlerLoader = new SqlHandlerLoader(context, handlerBuilder);
        operationResources(request.filename()).forEach(handlerLoader::loadFromResource);
        return GroovyScriptValidator.validate(handlerLoader::parse, handlerBuilder::build, request.scriptText(), request.operation());
    }

    /**
     * Builds the schema, connecting to the database and discovering it (as {@code test()} and actual
     * data operations do) whenever the required connection parameters are present. Only falls back to
     * building the schema from local Groovy/YAML definitions alone when the configuration is still
     * incomplete (e.g. a brand new, not yet filled in wizard form) — connecting is pointless then and
     * would only fail. Does not undo a richer, DB-discovered schema already produced by a prior
     * {@link #initializeHandlers()} on this instance.
     */
    private void initializeCore() {
        if (closed.get()) {
            return;
        }
        synchronized (this) {
            if (reinitializeOnEachCall || !coreInitialized) {
                boolean allowConnection = context.configuration().isComplete();
                initialize0(allowConnection);
                coreInitialized = true;
                fullyInitialized = allowConnection;
            }
        }
    }

    /** Ensures a live connection pool exists and, if enabled, the schema has been discovered from the database. */
    private void initializeHandlers() {
        if (closed.get()) {
            return;
        }
        synchronized (this) {
            if (reinitializeOnEachCall || !fullyInitialized) {
                initialize0(true);
                coreInitialized = true;
                fullyInitialized = true;
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

        // Detect the database structure once. The same snapshot is translated into the normal
        // connector schema and, in development mode, exposed as raw conndev_SqlTable metadata.
        // Skipped entirely when the configuration is incomplete: SqlSchemaDetector's constructor
        // itself needs a live connection, and there is none to detect anything from.
        List<SqlTableInfo> tables;
        if (!allowConnection) {
            context.setSqlTemplates(SQLTemplates.DEFAULT);
            tables = new ArrayList<>();
        } else {
            var tableFilter = new TableFilter(
                    Boolean.TRUE.equals(context.configuration().getScanTables()),
                    Boolean.TRUE.equals(context.configuration().getScanViews()),
                    context.configuration().getScanTableFilter(),
                    context.configuration().getScanViewFilter(),
                    context.configuration().getScanExcludeTables(),
                    context.configuration().getScanExcludeViews()
            );

            SqlSchemaDetector detector;
            try {
                detector = new SqlSchemaDetector(context);
            } catch (SQLException ex) {
                throw new ConnectionFailedException(ex.getMessage(), ex);
            }
            detector.setTableFilter(tableFilter);

            var templates = detector.getSQLTemplates();
            if (templates == null) {
                templates = SQLTemplates.DEFAULT;
            }
            context.setSqlTemplates(templates);

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
        }

        var additional = new ArrayList<ObjectClassInfo>();
        if (Boolean.TRUE.equals(context.configuration().getDevelopmentMode())) {
            additional.addAll(ConnDevSchema.objectClassInfos(
                    List.of(ConnDevSchema.embeddedBlock(SQL_BLOCK, SQL_BLOCK_TYPE)),
                    List.of(ConnDevSchema.embeddedBlock(SQL_BLOCK, SQL_ATTRIBUTE_BLOCK_TYPE))));
            additional.add(sqlObjectClassBlock());
            additional.add(sqlAttributeBlock());
            additional.add(SqlDevelopmentMode.tableObjectClassInfo());
        }

        // Translate into framework schema model. Populates the builder only — rule dispatch and
        // freezing happen next, explicitly, external to both the translator and the builder.
        var translator = new SqlSchemaTranslator(builder, tables);
        translator.connector(getClass(), context).translate(additional);
        // Join validation requires discovered tables; an incomplete setup has only local definitions.
        if (allowConnection) {
            translator.applyRules();
        }
        builder.applyStructuralRules();
        context.schema(builder.build());

        // Populate table info map for custom query support
        populateTableInfo(context, tables);
        // Initialize handlers

        var handlerBuilder = new SqlOperationSupportBuilderImpl(context);

        var handlerLoader = new SqlHandlerLoader(context, handlerBuilder);
        initializeObjectClassHandler(handlerLoader);


        // Trigger defaults for each and every object class, then re-evaluate resource rules'
        // handler effect (MappingAction#applyToHandler) against each object class's detected
        // table — handlers didn't exist yet at schema build() time, so this couldn't happen any
        // earlier.
        if (context.schema() != null) {
            for (var ocBuilder : builder.allObjectClassBuilders()) {
                // Skip embedded child tables - they are resolved as attributes on the parent
                if (ocBuilder.embedded()) {
                    continue;
                }
                var ocHandlerBuilder = handlerBuilder.objectClass(ocBuilder.objectClass().getObjectClassValue());
                translator.applyHandlerRulesFor(ocBuilder, ocHandlerBuilder);
            }
        }

        var handlers = handlerBuilder.build();
        // FIXME: This should be somehow unified
        if (Boolean.TRUE.equals(context.configuration().getDevelopmentMode())) {
            var name = new ObjectClass(ConnDevObjectClass.OBJECT_CLASS_NAME);
            var handler = CompositeObjectClassHandler.of(name,ObjectSearchOperation.class, new SqlObjectClassDevHandler(context));
            handlers.put(name, handler);

            var tableName = new ObjectClass(SqlDevelopmentMode.TABLE_OC_NAME);
            var tableHandler = CompositeObjectClassHandler.of(
                    tableName, ObjectSearchOperation.class, new SqlTableDevHandler(context));
            handlers.put(tableName, tableHandler);
        }

        context.handlers(handlers);
    }

    private static void populateTableInfo(SqlBaseContext context, List<SqlTableInfo> tables) {
        context.setDetectedTables(tables);
        var tableMap = new LinkedHashMap<String, SqlTableInfo>();
        for (SqlTableInfo table : tables) {
            // Use lowercase table name as key for case-insensitive lookup
            tableMap.put(table.getName().toLowerCase(), table);
        }
        context.setTableInfos(tableMap);
    }

    /** The object-class-level {@code sql} block: DB schema and table name. */
    private static ObjectClassInfo sqlObjectClassBlock() {
        var builder = new ObjectClassInfoBuilder();
        builder.setType(SQL_BLOCK_TYPE);
        builder.setEmbedded(true);
        builder.addAttributeInfo(AttributeInfoBuilder.build("table", String.class));
        builder.addAttributeInfo(AttributeInfoBuilder.build("schema", String.class));
        return builder.build();
    }

    /** The attribute-level {@code sql} block: the mapped column and its native SQL type. */
    private static ObjectClassInfo sqlAttributeBlock() {
        var builder = new ObjectClassInfoBuilder();
        builder.setType(SQL_ATTRIBUTE_BLOCK_TYPE);
        builder.setEmbedded(true);
        builder.addAttributeInfo(AttributeInfoBuilder.build("column", String.class));
        builder.addAttributeInfo(AttributeInfoBuilder.build("type", String.class));
        return builder.build();
    }

    /**
     * YAML counterpart of the schema branch above — {@code compile} only runs the static syntax
     * check, {@code build} loads the candidate for real via {@link YamlSchemaLoader}, bound
     * directly onto the same builder the siblings already populated (no separate inert copy, as
     * connector-scimrest's schema loader needs).
     */
    private ScriptValidationResult validateYamlSchema(ScriptValidationRequest request) {
        var builder = new SqlSchemaBuilderImpl(getClass(), context);
        var siblingLoader = new SqlSchemaDefinitionLoader(builder, context.configuration().groovyContext());
        schemaResources(request.filename()).forEach(siblingLoader::loadFromResource);

        return YamlScriptValidator.validate(
                request,
                document -> GroovySyntaxChecker.checkObjectClasses(document, SqlObjectClassSchemaBuilder.class,
                        new GroovyScriptCompiler(context.configuration().groovyContext())),
                () -> new YamlSchemaLoader(builder).load(request.scriptText()),
                () -> {
                    builder.applyStructuralRules();
                    builder.build();
                });
    }

    /**
     * YAML counterpart of the operations branch above — same split as {@link #validateYamlSchema}.
     * No {@code authentication} block on the SQL side, so {@link
     * GroovySyntaxChecker#checkObjectClasses} applies directly (not REST's {@code
     * checkOperations}).
     */
    private ScriptValidationResult validateYamlOperations(ScriptValidationRequest request) {
        var handlerBuilder = new SqlOperationSupportBuilderImpl(context);
        var handlerLoader = new SqlHandlerLoader(context, handlerBuilder);
        operationResources(request.filename()).forEach(handlerLoader::loadFromResource);
        var compiler = new GroovyScriptCompiler(context.configuration().groovyContext());

        return YamlScriptValidator.validate(
                request,
                document -> GroovySyntaxChecker.checkObjectClasses(document, SqlObjectOperationSupportBuilder.class, compiler),
                () -> new YamlSqlOperationsLoader(handlerBuilder, compiler).load(
                        LocatedDocument.parse(request.filename() != null ? request.filename() : "candidate.yaml", request.scriptText())),
                handlerBuilder::build);
    }

    private void checkInitialized() {
        if (context == null || closed.get()) {
            throw new IllegalStateException("Connector not initialized. Call init() first.");
        }
    }
}
