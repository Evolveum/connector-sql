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

import java.util.Locale;

/**
 * Detects UID from a column matching a specific name pattern.
 * Falls back to single-PK detection if no column matches.
 */
public class ColumnsMatchingPatternAsUidRule implements SchemaMappingRule {

    private final String columnNamePattern;

    public ColumnsMatchingPatternAsUidRule(String columnNamePattern) {
        this.columnNamePattern = columnNamePattern.toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlColumnMeta column) {
        if (column != null) return false;
        boolean hasMatchByName = table.getColumns().stream()
                .anyMatch(c -> c.getName().toLowerCase(Locale.ROOT).equals(columnNamePattern));
        if (hasMatchByName) return true;
        long pkCount = table.getColumns().stream().filter(SqlColumnMeta::isPrimaryKey).count();
        return pkCount == 1;
    }

    @Override
    public UidDetectionAction createAction(SqlTableInfo table, SqlColumnMeta column) {
        var uid = table.getColumns().stream()
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).equals(columnNamePattern))
                .findFirst()
                .orElseGet(() -> table.getColumns().stream()
                        .filter(SqlColumnMeta::isPrimaryKey)
                        .findFirst()
                        .orElse(null));
        return uid != null ? new NamedColumnUidAction(uid) : null;
    }

    private record NamedColumnUidAction(SqlColumnMeta column) implements UidDetectionAction {

    }
}
