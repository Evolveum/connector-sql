/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync;

/**
 * Builder for configuring the sync operation of a SQL object class.
 *
 * <p>Used as the delegate object of the {@code sync} closure in Groovy script:
 *
 * <pre>
 * objectClass("User") {
 *     sync {
 *         enabled = true
 *         strategy = SyncStrategy.TIMESTAMP_POLLING
 *         timestampColumn = "updated_at"
 *         deletedAtColumn = "deleted_at"
 *     }
 * }
 * </pre>
 */
public class SyncOperationBuilder {

    private boolean enabled = true;
    private SyncStrategy strategy = SyncStrategy.TIMESTAMP_POLLING;
    private String timestampColumn;
    private String deletedAtColumn;
    private String auditTable;
    private boolean databaseToken;

    /**
     * Sets whether sync is enabled for this object class.
     */
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the current state of the enabled flag.
     */
    public boolean getEnabled() {
        return enabled;
    }

    /**
     * Sets the sync strategy (default {@link SyncStrategy#TIMESTAMP_POLLING}).
     */
    public void strategy(SyncStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Sets the sync strategy by name (default {@link SyncStrategy#TIMESTAMP_POLLING}).
     *
     * @param strategyName strategy name, matching one of the enum values
     */
    public void strategy(String strategyName) {
        try {
            this.strategy = SyncStrategy.valueOf(strategyName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown sync strategy: " + strategyName
                    + ". Available strategies: " + SyncStrategy.TIMESTAMP_POLLING
                    + ", " + SyncStrategy.AUDIT_TABLE + ", " + SyncStrategy.POSTGRESQL_XMIN
                    + ", " + SyncStrategy.ORACLE_ROWVERSION + ", " + SyncStrategy.SQLITE_ROWID, e);
        }
    }

    /**
     * Gets the current sync strategy.
     */
    public SyncStrategy getStrategy() {
        return strategy;
    }

    /**
     * Sets the name of the column that holds the last-updated timestamp (default {@code "updated_at"}).
     * Required for {@link SyncStrategy#TIMESTAMP_POLLING} strategy.
     */
    public void timestampColumn(String timestampColumn) {
        this.timestampColumn = timestampColumn;
    }

    /**
     * Gets the timestamp column name.
     */
    public String getTimestampColumn() {
        return timestampColumn;
    }

    /**
     * Sets the name of the column that holds the "deleted at" timestamp for soft-delete detection.
     *
     * <p>If set and not null, rows where
     * {@code deletedAtColumn IS NOT NULL AND deletedAtColumn > token} are reported as DELETE deltas.
     */
    public void deletedAtColumn(String deletedAtColumn) {
        this.deletedAtColumn = deletedAtColumn;
    }

    /**
     * Gets the deleted-at column name.
     */
    public String getDeletedAtColumn() {
        return deletedAtColumn;
    }

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
    public void auditTable(String auditTable) {
        this.auditTable = auditTable;
    }

    /**
     * Gets the audit table name.
     */
    public String getAuditTable() {
        return auditTable;
    }

    /**
     * If true, the sync token is stored as a database-native value (e.g. postgres_xmin, rowid)
     * rather than system epoch millis. This allows the connector to use database-native
     * change tracking.
     */
    public void databaseToken(boolean databaseToken) {
        this.databaseToken = databaseToken;
    }

    /**
     * Gets whether database-native tokens are used.
     */
    public boolean getDatabaseToken() {
        return databaseToken;
    }

    /**
     * Builds the {@link SyncOperationDefinition} from the current configuration.
     */
    public SyncOperationDefinition build() {
        String tsCol = timestampColumn;
        if (strategy == SyncStrategy.TIMESTAMP_POLLING && (tsCol == null || tsCol.isBlank())) {
            tsCol = "updated_at";
        }
        if (strategy == SyncStrategy.POSTGRESQL_XMIN) {
            tsCol = "xmin";
        }
        return new SyncOperationDefinition(
                enabled,
                strategy,
                tsCol,
                deletedAtColumn,
                auditTable,
                databaseToken
        );
    }
}