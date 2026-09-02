/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import java.util.List;

import com.evolveum.polygon.sql.base.schema.ChildTableRelationship.JoinKey;

/**
 * Configuration for an attribute stored in an owned child table.
 * For simple attribute joins (FK + one value column), {@code valueColumn} specifies
 * the column to extract as a scalar value instead of building an EmbeddedObject.
 */
public record SqlChildJoinConfig(
        String parentTable,
        String childTable,
        List<JoinKey> joinKeys,
        boolean multiValued,
        String targetAttributeName,
        String valueColumn
) {
    public SqlChildJoinConfig {
        joinKeys = List.copyOf(joinKeys);
        if (joinKeys.isEmpty()) {
            throw new IllegalArgumentException("A child-table relationship requires at least one join key");
        }
    }

    public SqlChildJoinConfig(
            String parentTable, String childTable, List<JoinKey> joinKeys,
            boolean multiValued, String targetAttributeName) {
        this(parentTable, childTable, joinKeys, multiValued, targetAttributeName, null);
    }
}
