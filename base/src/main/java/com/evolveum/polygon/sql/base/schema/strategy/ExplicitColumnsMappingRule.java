/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.schema.SchemaMappingAction;
import com.evolveum.polygon.sql.base.schema.SchemaMappingRule;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

/**
 * Filters columns to include only those that are explicitly defined in Groovy/YAML scripts.
 * <p>
 * When {@code onlyExplicitlyListed} is enabled at the schema builder level, this strategy
 * restricts detected columns to only those with an explicit attribute definition.
 */
public class ExplicitColumnsMappingRule implements SchemaMappingRule {

    private final SqlSchemaBuilderRef schemaBuilderRef;

    public ExplicitColumnsMappingRule(SqlSchemaBuilderRef schemaBuilderRef) {
        this.schemaBuilderRef = schemaBuilderRef;
    }

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlColumnMeta column) {
        if (column == null) {
            return false;
        }
        var builder = schemaBuilderRef.get();
        if (builder == null) {
            return false;
        }
        return builder.isOnlyExplicitlyListed() && !isExplicitColumn(table, column.getName());
    }

    @Override
    public SchemaMappingAction createAction(SqlTableInfo table, SqlColumnMeta column) {
        // Explicit columns strategy doesn't create actions; it filters by returning
        // null for columns that shouldn't be included. The translator handles this
        // by checking if the strategy is applicable and skipping the column.
        return null;
    }

    private boolean isExplicitColumn(SqlTableInfo table, String columnName) {
        var builder = schemaBuilderRef.get();
        if (builder == null) {
            return true;
        }
        for (var oc : builder.allObjectClassBuilders()) {
            var sqlSchema = oc.sql().schema();
            var sqlTable = oc.sql().table();
            if (sqlSchema == null) sqlSchema = "";
            boolean schemaMatches = sqlSchema.isEmpty() || sqlSchema.equals(table.getSchema());
            if (schemaMatches && sqlTable != null && sqlTable.equals(table.getName())) {
                return oc.hasExplicitRemoteName(columnName);
            }
        }
        return false;
    }

    /**
     * Lazy reference to SqlSchemaBuilderImpl to avoid circular dependency.
     */
    public interface SqlSchemaBuilderRef {
        SqlSchemaBuilderImpl get();
    }
}
