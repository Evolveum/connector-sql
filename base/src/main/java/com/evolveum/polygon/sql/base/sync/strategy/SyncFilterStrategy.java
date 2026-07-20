/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync.strategy;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.sql.RelationalPathBase;

/**
 * Strategy for filtering rows during sync queries.
 */
public interface SyncFilterStrategy {

    /**
     * Builds a filter for the main sync query.
     */
    BooleanExpression applySyncFilter(RelationalPathBase<?> path);

    /**
     * Builds a filter for the tombstone query.
     */
    default BooleanExpression applyTombstoneFilter(
            RelationalPathBase<?> path,
            ComparablePath<?> syncColumn,
            Object lastSyncValue) {
        return null;
    }

    default String deleteMarkerColumn() {
        return null;
    }
}