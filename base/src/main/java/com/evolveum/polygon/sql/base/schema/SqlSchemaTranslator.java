/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.sql.base.build.api.*;
import com.evolveum.polygon.sql.base.schema.strategy.*;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.spi.Connector;

import java.util.*;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

/**
 * Translates JDBC-detected tables into the conndev {@link BaseSchema} using a unified
 * strategy-based approach. Detection strategies examine table and column metadata,
 * produce {@link SchemaMappingAction} instances that modify both schema definitions and
 * handler configurations.
 *
 * <p>Flow within {@link #translateTable(SqlTableInfo)}:
 * <ol>
 *   <li>Correlate table with existing Groovy-defined object class or create new one</li>
 *   <li>Run table-level detection strategies (view, UID, embedded)</li>
 *   <li>Filter columns (explicit-only when configured)</li>
 *   <li>Create attributes and run column-level detection strategies</li>
 *   <li>Apply UID mapping from table-level strategies</li>
 *   <li>Handle composite PK additional columns</li>
 *   <li>Collect handler actions for post-translation application</li>
 * </ol>
 *
 * @see SchemaMappingRule
 * @see SchemaMappingAction
 * @see SqlSchemaBuilderImpl
 */
public class SqlSchemaTranslator {

    private final SqlSchemaBuilderImpl builder;
    private final List<SqlTableInfo> tables;
    private final List<SchemaMappingRule> detectionStrategies = new ArrayList<>();
    private final Map<String, List<SchemaMappingAction>> handlerActions = new LinkedHashMap<>();
    private Class<? extends Connector> connectorClass;
    private ContextLookup contextLookup;

    public SqlSchemaTranslator(List<SqlTableInfo> tables) {
        this(null, tables);
    }

    public SqlSchemaTranslator(SqlSchemaBuilderImpl builder, List<SqlTableInfo> tables) {
        this.builder = builder == null
                ? new SqlSchemaBuilderImpl(placeholderConnectorClass(), null)
                : builder;
        this.tables = tables == null || tables.isEmpty() ? Collections.emptyList() : new ArrayList<>(tables);
        registerDefaultStrategies();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Connector> placeholderConnectorClass() {
        return (Class<? extends Connector>) (Class<?>) Object.class;
    }

    private void registerDefaultStrategies() {
        // Column-level strategies
        detectionStrategies.add(new NullableAttributesAreNotRequiredRule());
        detectionStrategies.add(new LargeTypesNotReturnedByDefaultRule());
        detectionStrategies.add(new PrimaryKeyIsNotUpdatableRule());
        detectionStrategies.add(new AutoIncrementColumnIsNotEditableRule());
        // Table-level strategies
        detectionStrategies.add(new ViewsShouldBeReadOnly());
        detectionStrategies.add(new SinglePrimaryKeyIsUidRule());
        detectionStrategies.add(new CompositePkUidMappingRule());
        detectionStrategies.add(new UniqueAttributeAsFallbackUidRule());
        // Explicit columns filter (applied at end when builder flag is set)
        detectionStrategies.add(new ExplicitColumnsMappingRule(() -> this.builder));
    }

    public SqlSchemaTranslator connector(Class<? extends Connector> connectorClass, ContextLookup contextLookup) {
        this.connectorClass = connectorClass;
        this.contextLookup = contextLookup;
        return this;
    }

    public SqlSchemaTranslator addStrategy(SchemaMappingRule strategy) {
        this.detectionStrategies.add(strategy);
        return this;
    }

    public SqlSchema translate(Collection<ObjectClassInfo> additionalObjectClasses) {
        additionalObjectClasses.forEach(builder::defineObjectClass);
        return translateInternal();
    }

    public Map<String, List<SchemaMappingAction>> getDetectedActions() {
        return Collections.unmodifiableMap(handlerActions);
    }

    @Deprecated
    public SqlSchema translate(Class<? extends Connector> connectorClass, ContextLookup contextLookup) {
        this.connectorClass = connectorClass;
        this.contextLookup = contextLookup;
        return translateInternal();
    }

    @Deprecated
    public SqlSchemaTranslator addStrategy(AttributeDetectionStrategy strategy) {
        return this;
    }

    private SqlSchema translateInternal() {
        for (SqlTableInfo table : tables) {
            translateTable(table);
        }
        return builder.build();
    }

    private void translateTable(SqlTableInfo table) {
        if (table == null || table.getColumns() == null || table.getColumns().isEmpty()) {
            return;
        }
        if (builder.getOnlyExplicitlyListed() != null && builder.getOnlyExplicitlyListed()
                && !hasCorrelatedBuilder(table)) {
            return;
        }

        // Phase 1: Correlate builder
        var objectClass = correlateBuilder(table);

        // Phase 2: Collect table-level actions (including UID strategy actions)
        List<SchemaMappingAction> tableActions = collectTableActions(table);

        // Phase 3: Create attributes with core setup and column-level strategies
        for (SqlColumnMeta column : getIncludedColumns(table)) {
            var attribute = (SqlAttributeBuilderImpl) objectClass.attribute(column.getName());
            setupCoreAttribute(attribute, column);
            for (SchemaMappingRule strategy : detectionStrategies) {
                if (strategy.checkIfApplicable(table, column)) {
                    var action = strategy.createAction(table, column);
                    if (action instanceof SchemaMappingAction.ColumnSpecific columnSpecific) {
                        columnSpecific.applyToSchema(objectClass, attribute);
                    }
                }
            }
        }

        // Phase 4: Apply table-level actions (UID renaming/composite PK handled by UID strategies)
        for (SchemaMappingAction action : tableActions) {
            action.applyToSchema(objectClass);
        }

        // Phase 5: Collect handler actions
        var className = objectClass.name();
        handlerActions.computeIfAbsent(className, k -> new ArrayList<>());
        handlerActions.get(className).addAll(tableActions);
    }

    @SuppressWarnings("unchecked")
    private SqlObjectClassSchemaBuilderImpl correlateBuilder(SqlTableInfo table) {
        var maybeClassName = detected(table.getName());
        return (SqlObjectClassSchemaBuilderImpl) builder.correlateObjectClass(
                o -> {
                    var sqlSchema = o.sql().schema();
                    var sqlTable = o.sql().table();
                    if (sqlSchema == null) sqlSchema = "";
                    if (sqlTable == null) sqlTable = "";
                    return (sqlSchema.isEmpty() || sqlSchema.equals(table.getSchema()))
                            && sqlTable.equals(table.getName());
                },
                maybeClassName,
                o -> o.sql().schema(detected(table.getSchema())).table(detected(table.getName()))
        );
    }

    private List<SchemaMappingAction> collectTableActions(SqlTableInfo table) {
        List<SchemaMappingAction> actions = new ArrayList<>();
        for (SchemaMappingRule strategy : detectionStrategies) {
            if (strategy.checkIfApplicable(table, null)) {
                var action = strategy.createAction(table, null);
                if (action != null) {
                    actions.add(action);
                }
            }
        }
        return actions;
    }



    private void setupCoreAttribute(SqlAttributeBuilder.Reference attribute, SqlColumnMeta column) {
        var sql = attribute.sql();
        sql.column(detected(column.getName()));
        var mapping = column.getValueMapping();
        if (mapping != null) {
            attribute.connId().type(detected(mapping.connIdType()));
            sql.valueMapping(DefinitionValue.detected(mapping));
        } else {
            attribute.connId().type(String.class);
        }
        if (column.getReferencedTable() != null && column.getForeignKeyName() != null) {
            attribute.subtype(column.getForeignKeyName());
        }
    }

    private List<SqlColumnMeta> getIncludedColumns(SqlTableInfo table) {
        List<SqlColumnMeta> columns = new ArrayList<>(table.getColumns());
        if (Boolean.TRUE.equals(builder.getOnlyExplicitlyListed())) {
            columns = filterExplicitColumns(table);
        }
        return columns;
    }

    private List<SqlColumnMeta> filterExplicitColumns(SqlTableInfo table) {
        if (!hasExplicitAttribute(table)) {
            return table.getColumns();
        }
        return table.getColumns().stream()
                .filter(col -> isExplicitColumn(table, col.getName()))
                .toList();
    }

    private boolean hasExplicitAttribute(SqlTableInfo table) {
        for (SqlObjectClassSchemaBuilderImpl oc : builder.allObjectClassBuilders()) {
            if (sqlMatches(oc, table)) {
                return !oc.getExplicitRemoteNames().isEmpty();
            }
        }
        return false;
    }

    private boolean isExplicitColumn(SqlTableInfo table, String columnName) {
        for (SqlObjectClassSchemaBuilderImpl oc : builder.allObjectClassBuilders()) {
            if (sqlMatches(oc, table)) {
                return oc.hasExplicitRemoteName(columnName);
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean hasCorrelatedBuilder(SqlTableInfo table) {
        List<SqlObjectClassSchemaBuilder> builders =
                (List<SqlObjectClassSchemaBuilder>) (List<?>) builder.allObjectClassBuilders();
        for (SqlObjectClassSchemaBuilder oc : builders) {
            if (sqlMatches(oc, table)) {
                return true;
            }
        }
        return false;
    }

    private boolean sqlMatches(SqlObjectClassSchemaBuilder oc, SqlTableInfo table) {
        var sqlSchema = oc.sql().schema();
        var sqlTable = oc.sql().table();
        return (sqlSchema.isEmpty() || sqlSchema.equals(table.getSchema()))
                && sqlTable.equals(table.getName());
    }
}
