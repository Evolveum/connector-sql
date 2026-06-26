package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.schema.AttributeDetectionStrategy;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Strategy that detects the ConnId UID from a single (non-composite)
 * primary key column.
 * <p>
 * If the table has a composite primary key, UID detection fails
 * and the strategy returns empty.
 */
public class SinglePkUidStrategy implements AttributeDetectionStrategy {

    @Override
    public Optional<SqlColumnMeta> resolveUid(SqlTableInfo table) {
        List<SqlColumnMeta> pks = table.getColumns().stream()
                .filter(SqlColumnMeta::isPrimaryKey)
                .collect(Collectors.toList());
        return pks.size() == 1 ? Optional.of(pks.getFirst()) : Optional.empty();
    }
}