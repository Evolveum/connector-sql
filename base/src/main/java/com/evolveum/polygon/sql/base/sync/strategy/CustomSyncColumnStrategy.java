/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;

/**
 * Sync column strategy that returns an explicitly configured column name.
 *
 * <p>Used when the user specifies the sync column via Groovy config:
 * <pre>
 * objectClass("User") {
 *     sync { syncColumn "last_modified_ts" }
 * }
 * </pre>
 * </p>
 */
public class CustomSyncColumnStrategy implements SyncColumnStrategy {

    private final String columnName;

    CustomSyncColumnStrategy(String columnName) {
        this.columnName = columnName;
    }

    @Override
    public String resolve(SqlObjectClassDefinition def) {
        return columnName;
    }
}