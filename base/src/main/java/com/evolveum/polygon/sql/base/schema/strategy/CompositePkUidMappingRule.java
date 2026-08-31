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

import java.util.List;

/**
 * Detects UID from a composite primary key.
 * Returns the first PK column as the UID and exposes additional PK columns.
 */
public class CompositePkUidMappingRule implements SqlResourceMappingRule {

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlObjectClassSchemaBuilder objectClass, SqlAttributeBuilder<SqlAttributeBuilder.Reference> attribute) {
        long pkCount = table.getColumns().stream().filter(SqlColumnMeta::isPrimaryKey).count();
        return pkCount >= 2;
    }

    @Override
    public UidDetectionAction createAction(SqlTableInfo table) {
        List<SqlColumnMeta> pks = table.getColumns().stream()
                .filter(SqlColumnMeta::isPrimaryKey)
                .toList();
        if (pks.isEmpty()) {
            return null;
        }
        return new CompositePkUidAction(pks);
    }

    private record CompositePkUidAction(List<SqlColumnMeta> pkColumns) implements UidDetectionAction {
        @Override
        public SqlColumnMeta column() {
            return pkColumns.getFirst();
        }

        @Override
        public List<SqlColumnMeta> getAdditionalPkColumns() {
            return pkColumns.subList(1, pkColumns.size());
        }
    }
}
