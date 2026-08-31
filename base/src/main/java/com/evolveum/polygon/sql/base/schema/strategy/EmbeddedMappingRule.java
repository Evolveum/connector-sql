/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import com.evolveum.polygon.sql.base.schema.SqlMappingAction;
import com.evolveum.polygon.sql.base.schema.SqlResourceMappingRule;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

/**
 * Detects whether a table should be marked as embedded based on its name
 * containing a parent table prefix (e.g., "user_address" for parent "user").
 */
public class EmbeddedMappingRule implements SqlResourceMappingRule {

    private final String parentTablePrefix;

    public EmbeddedMappingRule(String parentTablePrefix) {
        this.parentTablePrefix = parentTablePrefix.toLowerCase();
    }

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlObjectClassSchemaBuilder objectClass, SqlAttributeBuilder<SqlAttributeBuilder.Reference> attribute) {
        var tableName = table.getName().toLowerCase();
        return tableName.endsWith("_" + parentTablePrefix)
                || tableName.startsWith(parentTablePrefix + "_");
    }

    @Override
    public SqlMappingAction createAction(SqlTableInfo table) {
        return new SqlMappingAction() {
            @Override
            public void applyToSchema(SqlObjectClassSchemaBuilder objectClass) {
                objectClass.embedded(detected(true));
            }
        };
    }
}
