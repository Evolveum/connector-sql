/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlResourceMappingRule;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.schema.UidDetectionAction;

/**
 * Fallback UID detection for tables without a primary key.
 * Uses unique-constrained columns as a last resort (common for views without PK).
 */
public class UniqueAttributeAsFallbackUidRule implements SqlResourceMappingRule {

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlObjectClassSchemaBuilder objectClass, SqlAttributeBuilder<SqlAttributeBuilder.Reference> attribute) {
        long pkCount = table.getColumns().stream().filter(SqlColumnMeta::isPrimaryKey).count();
        if (pkCount >= 1) return false;
        return table.getColumns().stream()
                .anyMatch(c -> c.isUnique() && !c.isPrimaryKey());
    }

    @Override
    public UidDetectionAction createAction(SqlTableInfo table) {
        var uid = table.getColumns().stream()
                .filter(c -> c.isUnique() && !c.isPrimaryKey())
                .findFirst()
                .orElse(null);
        if (uid == null) {
            return null;
        }
        return new UidDetectionAction.SingleColumnUidAction(uid);
    }
}
