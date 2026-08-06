/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.schema.SchemaMappingRule;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.schema.UidDetectionAction;

/**
 * Detects UID from a single (non-composite) primary key column.
 * Only applicable when the table has exactly one PK column.
 */
public class SinglePrimaryKeyIsUidRule implements SchemaMappingRule {

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlColumnMeta column) {
        if (column != null) return false;
        long pkCount = table.getColumns().stream().filter(SqlColumnMeta::isPrimaryKey).count();
        return pkCount == 1;
    }

    @Override
    public UidDetectionAction createAction(SqlTableInfo table, SqlColumnMeta column) {
        var pk = table.getColumns().stream()
                .filter(SqlColumnMeta::isPrimaryKey)
                .findFirst()
                .orElse(null);
        return pk != null ? new SinglePkUidAction(pk) : null;
    }

    private record SinglePkUidAction(SqlColumnMeta column) implements UidDetectionAction { }
}
