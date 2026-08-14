/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync.strategy;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.sql.RelationalPathBase;

/**
 * Sync filter strategy for soft-delete tracking.
 *
 * <p>Applies <code>deletedAtColumn IS NULL</code> to sync queries, so only
 * non-deleted rows appear. Also builds a tombstone filter that finds
 * rows with <code>deletedAtColumn IS NOT NULL AND syncColumn &gt; lastValue</code>.</p>
 *
 * <p>Usage from Groovy:
 * <pre>
 * objectClass("User") { sync { softDeleteColumn "deleted_at" } }
 * </pre>
 * </p>
 */
public class SoftDeleteFilterStrategy implements SyncFilterStrategy {

    private final String deleteMarkerColumn;

    public SoftDeleteFilterStrategy(String deleteMarkerColumn) {
        this.deleteMarkerColumn = deleteMarkerColumn;
    }

    @Override
    public BooleanExpression applySyncFilter(RelationalPathBase<?> path) {
        return new PathBuilder<>(Object.class, deleteMarkerColumn).isNull();
    }

    @Override
    public BooleanExpression applyTombstoneFilter(
            RelationalPathBase<?> path, ComparablePath<?> syncColumn, Object lastSyncValue) {
        var deleted = new PathBuilder<>(Object.class, deleteMarkerColumn).isNotNull();
        if (lastSyncValue != null && ((Number) lastSyncValue).longValue() > 0) {
            @SuppressWarnings("unchecked")
            ComparablePath<Long> cp = (ComparablePath<Long>) syncColumn;
            return deleted.and(cp.gt(((Number) lastSyncValue).longValue()));
        }
        return deleted;
    }

    @Override
    public String deleteMarkerColumn() { return deleteMarkerColumn; }
}
