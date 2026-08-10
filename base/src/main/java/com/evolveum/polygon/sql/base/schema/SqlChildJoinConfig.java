/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

/**
 * Configuration for an embedded child table join.
 * For simple attribute joins (FK + one value column), {@code valueColumn} specifies
 * the column to extract as a scalar value instead of building an EmbeddedObject.
 */
public record SqlChildJoinConfig(
        String childTable,
        String parentJoinColumn,
        String childJoinColumn,
        boolean multiValued,
        String targetAttributeName,
        String valueColumn
) {
    public SqlChildJoinConfig(String childTable, String parentJoinColumn, String childJoinColumn,
                              boolean multiValued, String targetAttributeName) {
        this(childTable, parentJoinColumn, childJoinColumn, multiValued, targetAttributeName, null);
    }
}
