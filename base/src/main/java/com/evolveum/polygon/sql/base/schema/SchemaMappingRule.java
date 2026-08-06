/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

/**
 * Strategy for detecting properties from SQL schema metadata.
 * <p>
 * Strategies are evaluated against each table and optionally each column.
 * Applicable strategies produce {@link SchemaMappingAction} instances that
 * modify both schema definitions and handler configurations.
 * <p>
 * Usage follows a two-phase pattern:
 * <ol>
 *   <li>{@code checkIfApplicable(table, column)} determines if the strategy matches</li>
 *   <li>{@code createAction(table, column)} produces one or more actions to apply</li>
 * </ol>
 *
 * @see SchemaMappingAction
 * @see SqlSchemaTranslator
 */
public interface SchemaMappingRule {

    /**
     * Check if this strategy is applicable to the given table or column.
     *
     * @param table the SQL table metadata
     * @param column the column metadata, or {@code null} for table-level checks
     * @return {@code true} if this strategy has effects for this table/column
     */
    boolean checkIfApplicable(SqlTableInfo table, SqlColumnMeta column);

    /**
     * Create a detection action based on the detected property.
     * Called only when {@link #checkIfApplicable(SqlTableInfo, SqlColumnMeta)} returns {@code true}.
     *
     * @param table the SQL table metadata
     * @param column the column metadata, or {@code null} for table-level actions
     * @return one or more actions to apply, or empty if nothing to apply
     */
    default SchemaMappingAction createAction(SqlTableInfo table, SqlColumnMeta column) {
        return null;
    }
}
