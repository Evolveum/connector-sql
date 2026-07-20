/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Sync column strategy that prefers timestamp-based columns by name patterns.
 */
public class PreferTimestampSyncColumnStrategy implements SyncColumnStrategy {

    private static final List<String> EXACT = List.of(
            "modified", "updated_at", "last_modified", "changed_at",
            "last_updated", "date_modified", "date_updated");

    private static final Pattern SUFFIX = Pattern.compile("^(.+_)?(at|ts)$");
    private static final Pattern CREATED = Pattern.compile(".*_?created", Pattern.CASE_INSENSITIVE);

    @Override
    public String resolve(SqlObjectClassDefinition def) {
        // 1. Exact name matches
        for (SqlAttributeDefinition attr : def.attributes()) {
            var map = attr.sql();
            if (map == null || map.column() == null) continue;
            var nm = map.column().value().toLowerCase();
            if (EXACT.contains(nm)) return map.column().value();
        }

        // 2. Suffix matches (excluding creation-only)
        for (SqlAttributeDefinition attr : def.attributes()) {
            var map = attr.sql();
            if (map == null || map.column() == null) continue;
            var nm = map.column().value().toLowerCase();
            if (CREATED.matcher(nm).matches()) continue;
            if (SUFFIX.matcher(nm).matches()) return map.column().value();
        }

        return null;
    }
}
