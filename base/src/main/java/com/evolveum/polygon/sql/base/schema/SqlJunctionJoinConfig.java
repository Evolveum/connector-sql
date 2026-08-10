/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

/**
 * Configuration for a junction table join.
 */
public record SqlJunctionJoinConfig(
        String junctionTable,
        String parentJoinColumn,
        String junctionParentKey,
        String junctionTargetKey,
        String targetObjectClass
) {}
