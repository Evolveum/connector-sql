/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync;

/**
 * Strategy used by SQL connector to detect and report changes for sync operations.
 */
public enum SyncStrategy {

    /**
     * Poll by timestamp column: {@code SELECT * FROM table WHERE timestamp_column > token}.
     * Supports {@code deletedAtColumn} for soft-delete detection.
     */
    TIMESTAMP_POLLING,

    /**
     * Read from a separate audit table:
     * {@code SELECT t.* FROM sync_audit a JOIN table t ON a.uid = t.uid WHERE a.timestamp > token}.
     */
    AUDIT_TABLE,

    /**
     * Use PostgreSQL {@code xmin} column to detect changes.
     * The {@code timestampColumn} is ignored and {@code xmin} is used instead.
     */
    POSTGRESQL_XMIN,

    /**
     * Use Oracle {@code ROWVER} (sequence/trigger) to detect changes.
     */
    ORACLE_ROWVERSION,

    /**
     * Use SQLite {@code rowid} to detect changes.
     */
    SQLITE_ROWID
}