/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;

import java.util.List;

/**
 * Strategy for resolving the best sync column from an object class definition.
 */
public interface SyncColumnStrategy {

    /**
     * Tries to resolve a sync column name from the object class definition.
     *
     * @param def the object class definition
     * @return the column name as it appears in SQL, or {@code null} if this strategy
     *         cannot resolve a column, allowing other strategies in the chain to try
     */
    String resolve(SqlObjectClassDefinition def);

    /**
     * Default sync column strategy chain: prefer timestamp columns, fall through to PK.
     */
    static List<SyncColumnStrategy> defaultChain() {
        return List.of(new PreferTimestampSyncColumnStrategy(), new PrimaryKeySyncColumnStrategy());
    }
}
