package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.schema.AttributeDetectionStrategy;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Strategy for tables with composite primary keys.
 * <p>
 * Returns the first primary key column as the ConnId UID.
 * All primary key columns (including the UID column itself) are made
 * visible as attributes so the full composite key is queryable.
 * <p>
 * Unlike {@link SinglePkUidStrategy} which rejects composite PKs,
 * this strategy embraces them by selecting the first PK column as UID
 * and exposing all other PK columns in {@code detectColumns}.
 */
public class CompositePkUidStrategy implements AttributeDetectionStrategy {

    /**
     * Returns the first primary key column as the UID.
     * This always succeeds regardless of cardinality.
     */
    @Override
    public Optional<SqlColumnMeta> resolveUid(SqlTableInfo table) {
        List<SqlColumnMeta> pks = table.getColumns().stream()
                .filter(SqlColumnMeta::isPrimaryKey)
                .collect(Collectors.toList());
        return pks.isEmpty() ? Optional.empty() : Optional.of(pks.getFirst());
    }

    /**
     * Returns all columns, ensuring all primary key columns
     * (including additional PK columns beyond the UID) are
     * exposed as ConnId attributes.
     */
    @Override
    public List<SqlColumnMeta> detectColumns(SqlTableInfo table) {
        return table.getColumns();
    }
}