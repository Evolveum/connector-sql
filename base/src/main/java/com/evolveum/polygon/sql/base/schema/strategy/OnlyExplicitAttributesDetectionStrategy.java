/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.schema.AttributeDetectionStrategy;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Filters JDBC-detected columns to only those matching explicitly defined Groovy attributes
 * when {@code onlyExplicitlyListed} is true on the schema builder.
 *
 * <p>Respects object-class-level {@code onlyExplicitlyListed}:
 * if a Groovy-defined object class has no explicit attributes, all columns from JDBC
 * detection are returned (the object class-level default).
 *
 * <p>Used by {@link com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator} when
 * {@link SqlSchemaBuilderImpl#onlyExplicitlyListed(boolean)} is true.
 */
public class OnlyExplicitAttributesDetectionStrategy implements AttributeDetectionStrategy {

    private final SqlSchemaBuilderImpl builder;

    public OnlyExplicitAttributesDetectionStrategy(SqlSchemaBuilderImpl builder) {
        this.builder = builder;
    }

    @Override
    public List<SqlColumnMeta> detectColumns(SqlTableInfo table) {
        if (!builder.isOnlyExplicitlyListed()) {
            return table.getColumns();
        }
        return filterExplicit(table);
    }

    @Override
    public Optional<SqlColumnMeta> resolveUid(SqlTableInfo table) {
        if (!builder.isOnlyExplicitlyListed()) {
            return AttributeDetectionStrategy.super.resolveUid(table);
        }
        List<SqlColumnMeta> detected = detectColumns(table);
        return resolveUidFromDetected(detected);
    }

    /**
     * Check if a column has a matching explicit attribute in any correlated object class.
     */
    private boolean isExplicit(SqlTableInfo table, String columnName) {
        for (SqlObjectClassSchemaBuilderImpl oc : builder.allObjectClassBuilders()) {
            if (sqlMatches(oc, table)) {
                return oc.hasExplicitRemoteName(columnName);
            }
        }
        return false;
    }

    private List<SqlColumnMeta> filterExplicit(SqlTableInfo table) {
        if (containsExplicitAttribute(table)) {
            return table.getColumns().stream()
                    .filter(col -> isExplicit(table, col.getName()))
                    .collect(Collectors.toList());
        }
        return table.getColumns();
    }

    private boolean containsExplicitAttribute(SqlTableInfo table) {
        for (SqlObjectClassSchemaBuilderImpl oc : builder.allObjectClassBuilders()) {
            if (sqlMatches(oc, table)) {
                return !oc.getExplicitRemoteNames().isEmpty();
            }
        }
        return false;
    }

    private boolean sqlMatches(SqlObjectClassSchemaBuilderImpl oc, SqlTableInfo table) {
        var sqlSchema = oc.sql().schema();
        var sqlTable = oc.sql().table();
        if (sqlSchema == null) sqlSchema = "";
        boolean schemaMatches = sqlSchema.isEmpty() || sqlSchema.equals(table.getSchema());
        return schemaMatches && sqlTable != null && sqlTable.equals(table.getName());
    }

    private static Optional<SqlColumnMeta> resolveUidFromDetected(List<SqlColumnMeta> detected) {
        if (detected.isEmpty()) {
            return Optional.empty();
        }
        List<SqlColumnMeta> pks = detected.stream()
                .filter(SqlColumnMeta::isPrimaryKey)
                .collect(Collectors.toList());
        return pks.size() == 1 ? Optional.of(pks.getFirst()) : Optional.empty();
    }
}