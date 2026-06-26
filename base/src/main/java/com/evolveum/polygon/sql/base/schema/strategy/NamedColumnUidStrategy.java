package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.schema.AttributeDetectionStrategy;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

import java.util.List;
import java.util.Optional;

/**
 * Strategy that detects the ConnId UID from a column matching a
 * specific name pattern (e.g., "id", "uid", "*_uuid").
 * <p>
 * Falls back to the default single-PK detection if no column matches
 * the provided pattern.
 */
public class NamedColumnUidStrategy implements AttributeDetectionStrategy {

    private final String columnNamePattern;

    public NamedColumnUidStrategy(String columnNamePattern) {
        this.columnNamePattern = columnNamePattern.toLowerCase();
    }

    @Override
    public Optional<SqlColumnMeta> resolveUid(SqlTableInfo table) {
        return table.getColumns().stream()
                .filter(c -> c.getName().toLowerCase().equals(columnNamePattern))
                .findFirst()
                .or(() -> fallbackToSinglePk(table));
    }

    private Optional<SqlColumnMeta> fallbackToSinglePk(SqlTableInfo table) {
        List<SqlColumnMeta> pks = table.getColumns().stream()
                .filter(SqlColumnMeta::isPrimaryKey)
                .toList();
        return pks.size() == 1 ? Optional.of(pks.getFirst()) : Optional.empty();
    }
}