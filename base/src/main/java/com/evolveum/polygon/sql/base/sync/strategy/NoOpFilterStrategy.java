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
 * NoOp sync filter strategy — no filtering applied.
 *
 * <p>All rows matching the sync column range are returned,
 * including soft-deleted rows (they appear as CREATE_OR_UPDATE
 * deltas, not DELETE deltas).</p>
 *
 * <p>This is the default strategy; use {@link SoftDeleteFilterStrategy}
 * to enable soft-delete detection.</p>
 */
public class NoOpFilterStrategy implements SyncFilterStrategy {

    public static final NoOpFilterStrategy INSTANCE = new NoOpFilterStrategy();

    @Override
    public BooleanExpression applySyncFilter(RelationalPathBase<?> path) {
        return null;
    }

    @Override
    public BooleanExpression applyTombstoneFilter(
            RelationalPathBase<?> path, ComparablePath<?> syncColumn, Object lastSyncValue) {
        return null;
    }
}
