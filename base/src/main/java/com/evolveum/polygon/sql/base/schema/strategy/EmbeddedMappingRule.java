/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.schema.SchemaMappingAction;
import com.evolveum.polygon.sql.base.schema.SchemaMappingRule;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

/**
 * Detects whether a table should be marked as embedded based on its name
 * containing a parent table prefix (e.g., "user_address" for parent "user").
 */
public class EmbeddedMappingRule implements SchemaMappingRule {

    private final String parentTablePrefix;

    public EmbeddedMappingRule(String parentTablePrefix) {
        this.parentTablePrefix = parentTablePrefix.toLowerCase();
    }

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlColumnMeta column) {
        if (column != null) {
            return false;
        }
        var tableName = table.getName().toLowerCase();
        return tableName.endsWith("_" + parentTablePrefix)
                || tableName.startsWith(parentTablePrefix + "_");
    }

    @Override
    public SchemaMappingAction createAction(SqlTableInfo table, SqlColumnMeta column) {
        return new EmbeddedDetectionAction();
    }

    /**
     * Action that marks the object class as embedded.
     */
    private static class EmbeddedDetectionAction implements SchemaMappingAction {

        @Override
        public void applyToSchema(SqlObjectClassSchemaBuilderImpl objectClass) {
            objectClass.embedded(detected(true));
        }
    }
}
