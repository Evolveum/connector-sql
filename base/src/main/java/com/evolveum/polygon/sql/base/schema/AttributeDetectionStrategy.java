package com.evolveum.polygon.sql.base.schema;

import java.util.List;
import java.util.Optional;

/**
 * Strategy for detecting attribute configuration from SQL schema metadata.
 * <p>
 * Multiple strategies can be composed. Each strategy can filter columns,
 * determine ConnId UID mapping, and mark tables as embedded.
 */
public interface AttributeDetectionStrategy {

    /**
     * Determine which columns should be included as ConnId attributes.
     * Default includes all columns.
     *
     * @param table the SQL table to process
     * @return list of columns to include as attributes
     */
    default List<SqlColumnMeta> detectColumns(SqlTableInfo table) {
        return table.getColumns();
    }

    /**
     * Determine which column is the ConnId UID.
     * Default: single non-composite primary key column.
     *
     * @param table the SQL table
     * @return the UID column, or empty if not determinable
     */
    default Optional<SqlColumnMeta> resolveUid(SqlTableInfo table) {
        List<SqlColumnMeta> pks = table.getColumns().stream()
                .filter(SqlColumnMeta::isPrimaryKey)
                .toList();
        return pks.size() == 1 ? Optional.of(pks.getFirst()) : Optional.empty();
    }

    /**
     * Determine whether the table should be marked as embedded.
     * Default: false (not embedded).
     *
     * @param table the SQL table
     * @return true if the table is embedded
     */
    default boolean isEmbedded(SqlTableInfo table) {
        return false;
    }
}