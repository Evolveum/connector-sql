/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.schema.SqlAttributeMappingRule;
import com.evolveum.polygon.sql.base.schema.SqlMappingAction;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

/**
 * Filters columns to include only those that are explicitly defined in Groovy/YAML scripts.
 * <p>
 * When {@code onlyExplicitlyListed} is enabled at the schema builder level, this strategy
 * restricts detected columns to only those with an explicit attribute definition.
 * <p>
 * The actual column exclusion happens in {@code SqlSchemaTranslator#getIncludedColumns}, which
 * duplicates this same check independently before the column ever reaches attribute-rule
 * dispatch — this rule's {@code checkIfApplicable} is consulted for every already-included column
 * but {@link #createAction} never returns an action, so it currently has no observable effect.
 * Kept for parity with the pre-migration registration; not proposing to remove it here.
 */
public class ExplicitColumnsMappingRule implements SqlAttributeMappingRule {

    private final SqlSchemaBuilderRef schemaBuilderRef;

    public ExplicitColumnsMappingRule(SqlSchemaBuilderRef schemaBuilderRef) {
        this.schemaBuilderRef = schemaBuilderRef;
    }

    @Override
    public boolean checkIfApplicable(SqlAttributeMappingRule.Context context, SqlObjectClassSchemaBuilder objectClass, SqlAttributeBuilder<SqlAttributeBuilder.Reference> attribute) {
        var builder = schemaBuilderRef.get();
        if (builder == null) {
            return false;
        }
        return builder.isOnlyExplicitlyListed() && !isExplicitColumn(context.table(), context.column().getName());
    }

    @Override
    public SqlMappingAction createAction(SqlAttributeMappingRule.Context context) {
        // No effect — see class javadoc. Column exclusion is handled independently by
        // SqlSchemaTranslator#getIncludedColumns before dispatch ever reaches this rule.
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
