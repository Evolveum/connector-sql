/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;

/**
 * Represents a reference to a SQL table with type-aware column access from schema metadata.
 * 
 * <p>Used by custom query closures: {@code q.table("accounts", "a")}</p>
 */
@SuppressWarnings("rawtypes")
public class SqlTablePath {

    private final SqlBaseContext context;
    private final String tableName;
    private final String alias;

    SqlTablePath(SqlBaseContext context, String tableName, String alias) {
        this.context = context;
        this.tableName = tableName;
        this.alias = alias;
    }

    /** Table name from schema. */
    public String getTableName() {
        return tableName;
    }

    /** Query alias. */
    public String getAlias() {
        return alias;
    }

    /** Creates a column reference with type inference from schema metadata. */
    @SuppressWarnings("unused")
    public SqlColumnRef column(String name) {
        var meta = findColumnMeta(name);
        if (meta == null) {
            throw new IllegalArgumentException(
                    "Column '" + name + "' not found in table '" + tableName + "'");
        }
        return new SqlColumnRef(this, name, meta.getJavaType());
    }

    /**
     * Creates a column reference with explicit type override.
     * <p>Usage: {@code t.column("status", String.class)}</p>
     */
    @SuppressWarnings("unused")
    public SqlColumnRef column(String name, Class<?> type) {
        return new SqlColumnRef(this, name, type);
    }

    private SqlColumnMeta findColumnMeta(String name) {
        var tables = context.getTableInfos();
        if (tables == null) return null;
        var info = tables.get(tableName.toLowerCase());
        if (info == null) return null;
        for (SqlColumnMeta col : info.getColumns()) {
            if (col.getName().equalsIgnoreCase(name)) {
                return col;
            }
        }
        return null;
    }
}
