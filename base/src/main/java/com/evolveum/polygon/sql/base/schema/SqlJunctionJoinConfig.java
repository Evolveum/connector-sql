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
 * Configuration for a junction table join.
 */
public record SqlJunctionJoinConfig(
        String parentTable,
        String junctionTable,
        List<JoinKey> parentJoinKeys,
        List<JoinKey> targetJoinKeys,
        String targetObjectClass
) {
    public SqlJunctionJoinConfig {
        parentJoinKeys = List.copyOf(parentJoinKeys);
        targetJoinKeys = List.copyOf(targetJoinKeys);
        if (parentJoinKeys.isEmpty() || targetJoinKeys.isEmpty()) {
            throw new IllegalArgumentException("A junction relationship requires parent and target join keys");
        }
    }
}
