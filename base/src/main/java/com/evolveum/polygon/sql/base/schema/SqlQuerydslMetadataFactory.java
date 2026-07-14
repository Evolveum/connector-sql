/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.sql.base.connection.SqlQueryEngine;
import com.querydsl.sql.SQLTemplates;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating QueryDSL metadata from SqlTableInfo discovered by JDBC metadata.
 */
public class SqlQuerydslMetadataFactory {

    private final Map<String, QueryDSLMetadata> tableMetadata = new ConcurrentHashMap<>();
    private final SQLTemplates templates;
    private SqlQueryEngine queryEngine;

    public SqlQuerydslMetadataFactory(List<SqlTableInfo> tables, SQLTemplates templates) {
        this.templates = templates;
        if (tables != null) {
            for (SqlTableInfo table : tables) {
                if (table != null && table.getColumns() != null && !table.getColumns().isEmpty()) {
                    Map<String, QueryDSLMetadata.ColumnMeta> columns = buildColumnMap(table);
                    if (!columns.isEmpty()) {
                        // Store with both lowercase and original case for cross-database compatibility
                        // Different databases handle identifier case differently:
                        // H2 (default): unquoted identifiers uppercased, quoted identifiers case-sensitive
                        // H2 (MySQL mode): lowercase, case-insensitive
                        // PostgreSQL: unquoted lowercase, quoted case-sensitive
                        var tableName = table.getName();
                        var lowerKeyName = tableName.toLowerCase();
                        tableMetadata.put(lowerKeyName, new QueryDSLMetadata(tableName, columns));
                    }
                }
            }
        }
        this.queryEngine = new SqlQueryEngine(templates);
    }

    private Map<String, QueryDSLMetadata.ColumnMeta> buildColumnMap(SqlTableInfo table) {
        Map<String, QueryDSLMetadata.ColumnMeta> columns = new LinkedHashMap<>();
        for (SqlColumnMeta col : table.getColumns()) {
            if (col != null) {
                Class<?> javaType = resolveJavaType(col);
                columns.put(col.getName(), new QueryDSLMetadata.ColumnMeta(
                        col.getName(),
                        col.getTypeName(),
                        col.getTypeCode(),
                        col.getSize(),
                        javaType,
                        col.isNullable(),
                        col.isPrimaryKey(),
                        col.isUnique(),
                        col.isAutoIncrement(),
                        col.getReferencedTable(),
                        col.getReferencedColumn(),
                        col.getForeignKeyName()
                ));
            }
        }
        return columns;
    }

    private Class<?> resolveJavaType(SqlColumnMeta col) {
        if (col.getJavaType() instanceof Class<?> clazz) {
            return clazz;
        }
        return sqlTypeToJavaType(col.getTypeName());
    }

    private Class<?> sqlTypeToJavaType(String sqlTypeName) {
        if (sqlTypeName == null) {
            return String.class;
        }
        var upper = sqlTypeName.toUpperCase();
        switch (upper) {
            case "INT":
            case "INTEGER":
            case "BIGINT":
            case "SMALLINT":
            case "TINYINT":
                return Long.class;
            case "DECIMAL":
            case "NUMERIC":
            case "FLOAT":
            case "DOUBLE":
            case "REAL":
                return Double.class;
            case "BOOLEAN":
            case "BOOL":
                return Boolean.class;
            case "BLOB":
            case "BYTEA":
            case "VARBINARY":
                return byte[].class;
            default:
                return String.class;
        }
    }

    public Map<String, QueryDSLMetadata> getTableMetadata() {
        return tableMetadata;
    }

    public QueryDSLMetadata getMetadata(String tableName) {
        return tableMetadata.get(tableName.toLowerCase());
    }

    public Collection<String> getTableNames() {
        return tableMetadata.keySet();
    }

    public SQLTemplates getTemplates() {
        return templates;
    }

    public SqlQueryEngine getQueryEngine() {
        return queryEngine;
    }
}