/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.evolveum.polygon.sql.base.schema.QueryDSLMetadata;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * QueryDSL-based SQL query engine for SELECT operations.
 * Uses runtime {@link com.querydsl.core.types.dsl.PathBuilder}
 * for dynamic column resolution without compile-time metamodel generation.
 */
public class SqlQueryEngine {

    private final com.querydsl.sql.SQLTemplates templates;

    public SqlQueryEngine(com.querydsl.sql.SQLTemplates templates) {
        this.templates = templates;
    }

    /**
     * Executes a SELECT using only the specified columns (typically returned-by-default).
     * Each row is returned as a flat map keyed by the original SQL column name.
     */
    public List<Map<String, Object>> select(
            Connection conn, String tableName, QueryDSLMetadata metadata,
            List<String> selectedColumns,
            com.querydsl.core.types.Predicate predicate, List<String> orderBys,
            int pageSize, int pageOffset) throws SQLException {

        com.querydsl.sql.SQLQuery<?> query =
                new com.querydsl.sql.SQLQuery<>(conn, templates);

        // Use the actual table name from metadata for correct case handling
        com.querydsl.core.types.dsl.PathBuilder<Object> entityPath =
                new com.querydsl.core.types.dsl.PathBuilder<>(Object.class, metadata.getTableName());

        // Build list of select expressions (type-safe with correct path types)
        List<com.querydsl.core.types.Path<?>> selectExprs = new ArrayList<>();

        if (selectedColumns != null && !selectedColumns.isEmpty()) {
            for (String col : selectedColumns) {
                var colMeta = metadata.getColumn(col);
                if (colMeta != null) {
                    com.querydsl.core.types.Path<?> colPath = buildTypedColumnPath(entityPath, colMeta);
                    selectExprs.add(colPath);
                } else {
                    selectExprs.add(com.querydsl.core.types.dsl.Expressions.stringPath(col));
                }
            }
        } else {
            // No selection — fetch all columns
            for (QueryDSLMetadata.ColumnMeta colMeta : metadata.getColumns().values()) {
                com.querydsl.core.types.Path<?> colPath = buildTypedColumnPath(entityPath, colMeta);
                selectExprs.add(colPath);
            }
        }

        if (!selectExprs.isEmpty()) {
            query.select(selectExprs.toArray(new com.querydsl.core.types.Path[0]));
        }

        query.from(entityPath);

        if (predicate != null) {
            query.where(predicate);
        }

        // ORDER BY
        if (orderBys != null && !orderBys.isEmpty()) {
            for (String col : orderBys) {
                var colMeta = metadata.getColumn(col);
                if (colMeta != null) {
                    com.querydsl.core.types.Path<?> colPath = buildTypedColumnPath(entityPath, colMeta);
                    query.orderBy(((com.querydsl.core.types.dsl.ComparablePath<?>) buildTypedColumnPath(entityPath, colMeta)).asc());
                } else {
                    query.orderBy(com.querydsl.core.types.dsl.Expressions.stringPath(col).asc());
                }
            }
        }

        query.limit(pageSize).offset(pageOffset);

        List<Map<String, Object>> results = new ArrayList<>();
        List<com.querydsl.core.Tuple> rows = (List<com.querydsl.core.Tuple>) (List) query.fetch();
        for (com.querydsl.core.Tuple tuple : rows) {
            Map<String, Object> row = rowFromTuple(tuple, metadata, selectedColumns);
            results.add(row);
        }
        return results;
    }

    /**
     * Executes a SELECT query returning rows as maps (all columns, no typing).
     * Each row map is keyed by column name (ConnId attribute name).
     */
    public List<Map<String, Object>> select(
            Connection conn, String tableName,
            String[] columnNames,
            com.querydsl.core.types.Predicate predicate,
            List<String> orderBys,
            int pageSize, int pageOffset) throws SQLException {

        com.querydsl.sql.SQLQuery<?> query =
                new com.querydsl.sql.SQLQuery<>(conn, templates);

        com.querydsl.core.types.dsl.PathBuilder<Object> entityPath =
                new com.querydsl.core.types.dsl.PathBuilder<>(Object.class, tableName);

        for (String col : columnNames) {
            com.querydsl.core.types.Path<String> colPath = com.querydsl.core.types.dsl.Expressions.path(String.class, col);
            query.select(colPath);
        }

        query.from(entityPath);

        if (predicate != null) {
            query.where(predicate);
        }

        if (orderBys != null) {
            for (String col : orderBys) {
                query.orderBy(com.querydsl.core.types.dsl.Expressions.stringPath(col).asc());
            }
        }

        query.limit(pageSize).offset(pageOffset);

        List<Map<String, Object>> results = new ArrayList<>();
        List<com.querydsl.core.Tuple> rows = (List<com.querydsl.core.Tuple>) (List) query.fetch();
        for (com.querydsl.core.Tuple tuple : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columnNames.length && i < tuple.size(); i++) {
                var col = columnNames[i];
                row.put(col, tuple.get(i, String.class));
            }
            results.add(row);
        }
        return results;
    }

    private com.querydsl.core.types.Path<?> buildTypedColumnPath(
            com.querydsl.core.types.dsl.PathBuilder<Object> entityPath,
            QueryDSLMetadata.ColumnMeta colMeta) {
        // Use unqualified column paths to avoid H2's table.column syntax issues
        // with quoted table names
        Class<?> javaType = colMeta.getJavaType();
        if (javaType == null || javaType == String.class) {
            return com.querydsl.core.types.dsl.Expressions.path(String.class, colMeta.getName());
        }
        if (isAssignableFrom(javaType, java.lang.Number.class)) {
            return com.querydsl.core.types.dsl.Expressions.path(java.lang.Long.class, colMeta.getName());
        }
        if (isAssignableFrom(javaType, java.lang.Boolean.class)) {
            return com.querydsl.core.types.dsl.Expressions.path(Boolean.class, colMeta.getName());
        }
        // Binary / date / time default to String
        return com.querydsl.core.types.dsl.Expressions.path(String.class, colMeta.getName());
    }

    private boolean isAssignableFrom(Class<?> clazz, Class<?> supertype) {
        if (clazz == null) return false;
        if (supertype.isAssignableFrom(clazz)) return true;
        // Also check primitives
        Class<?> primitive;
        if (clazz == int.class) primitive = Integer.class;
        else if (clazz == long.class) primitive = Long.class;
        else if (clazz == double.class) primitive = Double.class;
        else if (clazz == boolean.class) primitive = Boolean.class;
        else primitive = clazz;
        return supertype.isAssignableFrom(primitive);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rowFromTuple(com.querydsl.core.Tuple tuple,
                                              QueryDSLMetadata metadata,
                                              List<String> selectedColumns) {
        Map<String, Object> row = new LinkedHashMap<>();
        List<String> colNames = (selectedColumns != null && !selectedColumns.isEmpty())
                ? selectedColumns
                : new ArrayList<>(metadata.getColumns().keySet());

        for (int i = 0; i < tuple.size(); i++) {
            String colName = i < colNames.size() ? colNames.get(i) : String.valueOf(i);
            var colMeta = metadata.getColumn(colName);
            if (colMeta != null) {
                Class<?> javaType = colMeta.getJavaType();
                if (javaType == String.class || javaType == null) {
                    row.put(colMeta.getName(), tuple.get(i, String.class));
                } else if (isAssignableFrom(javaType, java.lang.Number.class)) {
                    row.put(colMeta.getName(), tuple.get(i, Long.class));
                } else if (isAssignableFrom(javaType, java.lang.Boolean.class)) {
                    row.put(colMeta.getName(), tuple.get(i, Boolean.class));
                } else {
                    row.put(colMeta.getName(), tuple.get(i, String.class));
                }
            } else {
                row.put(colName, tuple.get(i, Object.class));
            }
        }
        return row;
    }
}