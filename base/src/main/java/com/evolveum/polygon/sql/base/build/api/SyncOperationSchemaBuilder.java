/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.sql.base.sync.SyncStrategy;

/**
 * Builder interface for configuring the sync operation of a SQL object class.
 *
 * <p>Used by {@link SqlObjectClassSchemaBuilder#sync()} to build a
 * {@link com.evolveum.polygon.sql.base.sync.SyncOperationDefinition} that is
 * later attached to the {@link SqlObjectClassDefinition} and used by {@code SqlSyncOperation}.
 */
public interface SyncOperationSchemaBuilder {

    /**
     * Sets whether sync is enabled for this object class (default: {@code true}).
     */
    void enabled(boolean enabled);

    /**
     * Returns the current state of the enabled flag.
     */
    boolean getEnabled();

    /**
     * Sets the sync strategy (default: {@link SyncStrategy#TIMESTAMP_POLLING}).
     */
    void strategy(SyncStrategy strategy);

    /**
     * Sets the sync strategy by name (default: {@link SyncStrategy#TIMESTAMP_POLLING}).
     *
     * @param strategyName strategy name, matching one of the enum values
     */
    void strategy(String strategyName);

    /**
     * Gets the current sync strategy.
     */
    SyncStrategy getStrategy();

    /**
     * Sets the name of the column that holds the last-updated timestamp
     * (default: {@code "updated_at"}). Required for
     * {@link SyncStrategy#TIMESTAMP_POLLING} strategy.
     */
    void timestampColumn(String timestampColumn);

    /**
     * Gets the timestamp column name.
     */
    String getTimestampColumn();

    /**
     * Sets the name of the column that holds the "deleted at" timestamp
     * for soft-delete detection.
     *
     * <p>If set and not {@code null}, rows where
     * {@code deletedAtColumn IS NOT NULL} are treated as soft-deleted.
     */
    void deletedAtColumn(String deletedAtColumn);

    /**
     * Gets the deleted-at column name.
     */
    String getDeletedAtColumn();

    /**
     * Sets the name of the audit table for {@link SyncStrategy#AUDIT_TABLE} strategy.
     *
     * <p>The audit table is expected to have columns:
     * <ul>
     *   <li>{@code uid} — the row identifier matching the main table</li>
     *   <li>{@code operation} — one of "CREATE", "UPDATE", "DELETE"</li>
     *   <li>{@code timestamp} — the change timestamp</li>
     * </ul>
     */
    void auditTable(String auditTable);

    /**
     * Gets the audit table name.
     */
    String getAuditTable();

    /**
     * If {@code true}, the sync token is stored as a database-native value
     * (e.g. postgres_xmin, oracle_scn, rowid) rather than system epoch millis.
     * Default: {@code false}.
     */
    void databaseToken(boolean databaseToken);

    /**
     * Gets whether database-native tokens are used.
     */
    boolean getDatabaseToken();
}