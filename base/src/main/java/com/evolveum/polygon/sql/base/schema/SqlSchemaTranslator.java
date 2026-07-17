/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.sql.base.build.api.*;
import com.evolveum.polygon.sql.base.connection.SqlValueMapping;
import com.evolveum.polygon.sql.base.schema.strategy.DefaultDetectionStrategy;
import com.evolveum.polygon.sql.base.schema.strategy.OnlyExplicitAttributesDetectionStrategy;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

/**
 * Translates JDBC-detected tables into the conndev {@link BaseSchema} using a unified
 * builder approach. Groovy scripts populate the builder directly, and JDBC detection
 * enhances it with SQL metadata.
 *
 * <p>No customizer intermediary — tables are correlated via SQL schema+table name matches
 * against Groovy-defined object classes, or created on-the-fly.
 *
 * @see SqlSchemaBuilderImpl
 */
public class SqlSchemaTranslator {

    private final SqlSchemaBuilderImpl builder;
    private final List<SqlTableInfo> tables;
    private final List<AttributeDetectionStrategy> strategies = new ArrayList<>();
    private Class<? extends Connector> connectorClass;
    private ContextLookup contextLookup;

    /**
     * Legacy-style constructor: creates an internal builder and processes the given tables.
     * Used by tests that don't have Groovy scripts.
     */
    public SqlSchemaTranslator(List<SqlTableInfo> tables) {
        this(null, tables);
    }

    /**
     * Constructor accepting a pre-built schema builder (from Groovy scripts or tests).
     *
     * @param builder the schema builder (may be null for legacy usage)
     * @param tables  the JDBC-detected tables to translate
     */
    public SqlSchemaTranslator(SqlSchemaBuilderImpl builder, List<SqlTableInfo> tables) {
        if (builder == null) {
            // Legacy mode: create internal builder (connectorClass used for schema building)
            // Use a raw cast to satisfy the type system - tests don't call builder.build() without setting the connector
            @SuppressWarnings("unchecked")
            Class<? extends Connector> placeholder = (Class<? extends Connector>) (Class<?>) Object.class;
            this.builder = new SqlSchemaBuilderImpl(placeholder, null);
            this.tables = tables == null || tables.isEmpty() ? Collections.emptyList() : new ArrayList<>(tables);
        } else {
            this.builder = builder;
            this.tables = tables == null || tables.isEmpty() ? Collections.emptyList() : new ArrayList<>(tables);
        }
        this.strategies.add(new DefaultDetectionStrategy());
    }

    /**
     * Set connector class and context for schema building.
     * Called automatically by {@link com.evolveum.polygon.sql.base.AbstractGroovySqlConnector}.
     *
     * @deprecated Use {@link #connector(Class, ContextLookup)} chain pattern instead.
     */
    @Deprecated
    public SqlSchema translate(Class<? extends Connector> connectorClass, ContextLookup contextLookup) {
        this.connectorClass = connectorClass;
        this.contextLookup = contextLookup;
        return translateInternal(connectorClass, contextLookup);
    }

    /**
     * Set connector class and context for schema building.
     * Called automatically by {@link com.evolveum.polygon.sql.base.AbstractGroovySqlConnector}.
     */
    public SqlSchemaTranslator connector(Class<? extends Connector> connectorClass, ContextLookup contextLookup) {
        this.connectorClass = connectorClass;
        this.contextLookup = contextLookup;
        return this;
    }

    public SqlSchemaTranslator addStrategy(AttributeDetectionStrategy strategy) {
        this.strategies.add(strategy);
        return this;
    }
    /**
     * Translate with additional object classes, using previously set connector/class and context.
     */
    @SuppressWarnings("UnusedMethod")
    public SqlSchema translate(Collection<ObjectClassInfo> additionalObjectClasses) {
        return translateInternal(this.connectorClass, this.contextLookup);
    }

    private SqlSchema translateInternal(Class<? extends Connector> connectorClass, ContextLookup contextLookup) {
        for (SqlTableInfo table : tables) {
            translateTable(table);
        }
        return builder.build();
    }

    /**
     * Translate a single SQL table into object class attributes.
     */
    private void translateTable(SqlTableInfo table) {
        if (table == null || table.getColumns() == null || table.getColumns().isEmpty()) {
            return;
        }
        // When onlyExplicitlyListed at schema builder level, skip tables with no Groovy definition
        if (builder.getOnlyExplicitlyListed() != null && builder.getOnlyExplicitlyListed()
                && !hasCorrelatedBuilder(table)) {
            return;
        }
        // Correlate builder: reuse existing Groovy-defined one or create new one
        var maybeClassName = detected(table.getName());
        @SuppressWarnings("unchecked")
        var objectClass = (SqlObjectClassSchemaBuilderImpl) builder.correlateObjectClass(
                o -> {
                    var sqlSchema = o.sql().schema();
                    var sqlTable = o.sql().table();
                    if (sqlSchema == null) sqlSchema = "";
                    if (sqlTable == null) sqlTable = "";
                    boolean schemaMatches = sqlSchema.isEmpty() || sqlSchema.equals(table.getSchema());
                    boolean tableMatches = sqlTable.equals(table.getName());
                    return schemaMatches && tableMatches;
                },
                maybeClassName,
                o -> {
                    o.sql().schema(detected(table.getSchema())).table(detected(table.getName()));
                }
        );
        // Set locator and namespace (not on interface, cast needed)
        objectClass.locator(table.getName());
        if (table.getSchema() != null) {
            objectClass.namespace(table.getSchema());
        }
        if (table.getRemarks() != null && !table.getRemarks().isBlank()) {
            objectClass.description(detected(table.getRemarks()));
        }
        // Apply detection strategies for embedded classification
        List<AttributeDetectionStrategy> effectiveStrategies = getEffectiveStrategies();
        if (effectiveStrategies.stream().anyMatch(strategy -> strategy.isEmbedded(table))) {
            objectClass.embedded(detected(true));
        }
        // Resolve UID column
        Optional<SqlColumnMeta> uidColumn = findUid(table, strategies);
        // Detect attribute columns
        List<SqlColumnMeta> attributeColumns = detectColumns(table, strategies);
        // Translate columns to attributes
        for (SqlColumnMeta column : attributeColumns) {
            translateColumn(column, objectClass);
        }
        // Map UID attribute - update ConnId name for the UID column
        if (uidColumn.isPresent() && attributeColumns.contains(uidColumn.get())) {
            @SuppressWarnings("unchecked")
            var uidAttr = (SqlAttributeBuilder.Reference) objectClass.attribute(uidColumn.get().getName());
            uidAttr.connId().name(Uid.NAME);
            // Handle composite primary keys: add additional PK columns to the UID mapping
            handleCompositePk(table, uidColumn.get(), objectClass);
        }
    }

    /**
     * Check if a correlated Groovy object class builder exists for the table.
     */
    @SuppressWarnings("unchecked")
    private boolean hasCorrelatedBuilder(SqlTableInfo table) {
        List<SqlObjectClassSchemaBuilder> builders = (List<SqlObjectClassSchemaBuilder>) (List<?>) builder.allObjectClassBuilders();
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
        boolean schemaMatches = sqlSchema.isEmpty() || sqlSchema.equals(table.getSchema());
        boolean tableMatches = sqlTable.equals(table.getName());
        return schemaMatches && tableMatches;
    }

    /**
     * Detect attribute columns using all registered strategies, applying explicit attribute
     * filtering when {@code onlyExplicitlyListed} is true.
     */
    private List<SqlColumnMeta> detectColumns(SqlTableInfo table, List<AttributeDetectionStrategy> strategies) {
        List<SqlColumnMeta> columns;
        if (table.getColumns() == null || table.getColumns().isEmpty()) {
            columns = Collections.emptyList();
        } else {
            columns = new ArrayList<>(table.getColumns());
        }
        for (AttributeDetectionStrategy strategy : strategies) {
            // Skip the OnlyExplicitAttributesDetectionStrategy here — handled below
            if (strategy instanceof OnlyExplicitAttributesDetectionStrategy) {
                continue;
            }
            List<SqlColumnMeta> filtered = strategy.detectColumns(table);
            if (filtered != null && !filtered.isEmpty()) {
                columns = filtered;
            }
        }
        // Apply explicit attribute filtering at the end if enabled
        if (Boolean.TRUE.equals(builder.getOnlyExplicitlyListed())) {
            var explicitFilter
                    = new OnlyExplicitAttributesDetectionStrategy(builder);
            columns = explicitFilter.detectColumns(table);
        }
        return columns;
    }

    private List<AttributeDetectionStrategy> getEffectiveStrategies() {
        return new ArrayList<>(strategies);
    }

    private Optional<SqlColumnMeta> findUid(SqlTableInfo table,
                                              List<AttributeDetectionStrategy> strategies) {
        Optional<SqlColumnMeta> uid = Optional.empty();
        for (AttributeDetectionStrategy strategy : strategies) {
            if (strategy instanceof OnlyExplicitAttributesDetectionStrategy) {
                continue;
            }
            uid = strategy.resolveUid(table);
            if (uid.isPresent()) {
                break;
            }
        }
        return uid;
    }

    private void translateColumn(SqlColumnMeta column, SqlObjectClassSchemaBuilder objectClass) {
        var attribute = objectClass.attribute(column.getName()).self();
        var connId = attribute.connId();
        connId.required(detected(!column.isNullable()));

        var sql = attribute.sql();
        sql.column(detected(column.getName()));
        SqlValueMapping mapping = SqlValueMapping.from(column.getTypeCode());

        if (column.getReferencedTable() != null) {
            attribute.objectClass(column.getReferencedTable());
            attribute.role(AttributeInfo.RoleInReference.SUBJECT);
            if (column.getForeignKeyName() != null) {
                attribute.subtype(column.getForeignKeyName());
            }
            // FIXME: Maybe additional metadata are needed to properly determine SUBJECT / OBJECT / OWNER relationship

        } else {
            connId.type(detected(mapping.connIdType()));
            sql.valueMapping(DefinitionValue.detected(mapping));
        }

        if (isLargeType(column.getTypeName())) {
            connId.returnedByDefault(detected(false));
        }
        if (column.isAutoIncrement()) {
            connId.creatable(detected(false));
            connId.updatable(detected(false));
        } else if (column.isPrimaryKey()) {
            connId.updatable(detected(false));
        }
        // FIXME: this should be called after tables correlation

    }

    private Class<?> connIdType(java.lang.reflect.Type javaType, String typeName) {
        if (javaType != null && javaType instanceof Class<?> clazz) {
            Class<?> resolved = mapJavaTypeToConnId(clazz);
            if (resolved != null) {
                return resolved;
            }
        }
        return connIdType(typeName);
    }

    private Class<?> mapJavaTypeToConnId(Class<?> javaType) {
        if (javaType == String.class) {
            return String.class;
        }
        if (BigInteger.class.isAssignableFrom(javaType)) {
            return Long.class;
        }
        if (BigDecimal.class.isAssignableFrom(javaType)) {
            return Double.class;
        }
        if (Date.class.isAssignableFrom(javaType)) {
            return String.class;
        }
        if (Time.class.isAssignableFrom(javaType)) {
            return String.class;
        }
        if (Timestamp.class.isAssignableFrom(javaType)) {
            return String.class;
        }
        if (Number.class.isAssignableFrom(javaType)) {
            return Long.class;
        }
        if (boolean.class.equals(javaType) || Boolean.class.equals(javaType)) {
            return Boolean.class;
        }
        if (double.class.equals(javaType) || Double.class.equals(javaType) ||
            Float.class.equals(javaType) || float.class.equals(javaType)) {
            return Double.class;
        }
        if (byte[].class.isAssignableFrom(javaType)) {
            return byte[].class;
        }
        return null;
    }

    private Class<?> connIdType(String typeName) {
        if (typeName == null) {
            return String.class;
        }
        var upper = typeName.toUpperCase();
        switch (upper) {
            case "VARCHAR": case "CHAR": case "TEXT": case "VARCHAR2":
            case "DATE": case "TIME": case "TIMESTAMP": case "DATETIME":
                return String.class;
            case "INTEGER": case "INT": case "SMALLINT": case "TINYINT": case "BIGINT":
                return Long.class;
            case "DECIMAL": case "NUMERIC": case "FLOAT": case "REAL": case "DOUBLE":
                return Double.class;
            case "BOOLEAN": case "BOOL":
                return Boolean.class;
            case "BLOB": case "BYTEA": case "VARBINARY":
                return byte[].class;
            default:
                return String.class;
        }
    }

    private boolean isLargeType(String typeName) {
        if (typeName == null) {
            return false;
        }
        var upper = typeName.toUpperCase();
        return upper.contains("BLOB") || upper.contains("CLOB")
                || upper.contains("BINARY") || upper.contains("VARBINARY");
    }

    /**
     * For tables with composite primary keys, add the additional PK columns to the UID mapping
     * so that the SQL connector treats them as part of a multi-column composite UID.
     */
    @SuppressWarnings("unchecked")
    private void handleCompositePk(SqlTableInfo table, SqlColumnMeta mainUid, SqlObjectClassSchemaBuilder objectClass) {
        var mainName = mainUid.getName();
        var extraPks = table.getColumns().stream()
                .filter(c -> c.isPrimaryKey() && !mainName.equals(c.getName()))
                .collect(Collectors.toList());
        if (extraPks.isEmpty()) { return; }
        var uidAttr = objectClass.attribute(mainUid.getName());
        if (uidAttr instanceof SqlAttributeBuilder.Reference refAttr) {
            var sql = refAttr.sql();
            for (var pk : extraPks) {
                var mapping = SqlValueMapping.from(pk.getTypeCode());
                sql.additionalColumns().column(pk.getName(), (SqlValueMapping) mapping);
            }
        }
    }
}