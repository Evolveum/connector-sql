/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync;

/**
 * Configuration for sync operation on a single SQL object class (table).
 *
 * <p>Applied per-table in a Groovy script or programmatically.
 *
 * <pre>
 * objectClass("User") {
 *     sync {
 *         enabled = true
 *         strategy = "TIMESTAMP_POLLING"
 *         timestampColumn = "updated_at"
 *         deletedAtColumn = "deleted_at"              // optional
 *         auditTable = "sync_audit"                    // optional
 *         databaseToken = false
 *     }
 * }
 * </pre>
 */
public record SyncOperationDefinition(
        boolean enabled,
        SyncStrategy strategy,
        String timestampColumn,
        String deletedAtColumn,
        String auditTable,
        boolean databaseToken
) {

    private static final String DEFAULT_TIMESTAMP_COLUMN = "updated_at";
    private static final String DEFAULT_DELETED_AT_COLUMN = "deleted_at";

    public SyncOperationDefinition {
        if (strategy == null) {
            throw new IllegalArgumentException("sync.strategy must be set");
        }
    }

    /**
     * Returns a copy with the given enabled flag.
     */
    public SyncOperationDefinition withEnabled(boolean enabled) {
        return new SyncOperationDefinition(enabled, strategy, timestampColumn, deletedAtColumn, auditTable, databaseToken);
    }

    /**
     * Returns a copy with the specified timestamp column.
     */
    public SyncOperationDefinition withTimestampColumn(String timestampColumn) {
        return new SyncOperationDefinition(enabled, strategy, timestampColumn, deletedAtColumn, auditTable, databaseToken);
    }
}