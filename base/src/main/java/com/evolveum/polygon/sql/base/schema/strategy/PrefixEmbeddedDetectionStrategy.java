package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.schema.AttributeDetectionStrategy;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

/**
 * Strategy that detects whether a table is embedded based on
 * its columns matching certain patterns (e.g., columns starting with
 * the table's parent prefix).
 * <p>
 * Default behavior: checks if all non-ID columns have names starting
 * with a specific prefix pattern.
 */
public class PrefixEmbeddedDetectionStrategy implements AttributeDetectionStrategy {

    private final String parentTablePrefix;

    public PrefixEmbeddedDetectionStrategy(String parentTablePrefix) {
        this.parentTablePrefix = parentTablePrefix.toLowerCase();
    }

    @Override
    public boolean isEmbedded(SqlTableInfo table) {
        String tableName = table.getName().toLowerCase();
        return tableName.endsWith("_" + parentTablePrefix)
                || tableName.startsWith(parentTablePrefix + "_");
    }
}