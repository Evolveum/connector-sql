/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync;

import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.sync.strategy.*;

import java.util.List;

/**
 * Bundles sync configuration for a single object class.
 */
public record SyncConfig(
        List<SyncColumnStrategy> columnStrategies,
        SyncFilterStrategy filterStrategy,
        String explicitSyncColumn,
        int pageSize,
        Boolean preferUpsertOverDelete
) {

    /**
     * Creates default sync config using the standard strategy chain.
     */
    public static SyncConfig defaultFor(SqlObjectClassDefinition def) {
        List<SyncColumnStrategy> strategies = List.of(
                new PreferTimestampSyncColumnStrategy(),
                new PrimaryKeySyncColumnStrategy()
        );
        return new SyncConfig(
                strategies,
                NoOpFilterStrategy.INSTANCE,
                null,
                200,
                true
        );
    }

    /**
     * Returns the effective sync column name.
     * If {@code explicitSyncColumn} is set, returns that directly.
     * Otherwise, resolves via the strategy chain.
     */
    public String resolveSyncColumn(SqlObjectClassDefinition def) {
        if (explicitSyncColumn != null) {
            return explicitSyncColumn;
        }
        for (SyncColumnStrategy strategy : columnStrategies) {
            var col = strategy.resolve(def);
            if (col != null) {
                return col;
            }
        }
        throw new IllegalStateException("No sync column found for object class "
                + def.objectClass() + ". Configure syncColumn in Groovy script.");
    }

    public SyncConfig withExplicitColumn(String col) {
        return new SyncConfig(columnStrategies, filterStrategy, col, pageSize, preferUpsertOverDelete);
    }

    public SyncConfig withPageSize(int pageSize) {
        return new SyncConfig(columnStrategies, filterStrategy, explicitSyncColumn, pageSize, preferUpsertOverDelete);
    }

public SyncConfig withFilterStrategy(SyncFilterStrategy fs) {
        return new SyncConfig(columnStrategies, fs, explicitSyncColumn, pageSize, preferUpsertOverDelete);
    }
}