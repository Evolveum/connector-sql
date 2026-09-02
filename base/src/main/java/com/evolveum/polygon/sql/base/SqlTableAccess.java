/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.connection.SqlConnection;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.dml.SQLDeleteClause;
import com.querydsl.sql.dml.SQLInsertClause;
import org.identityconnectors.framework.common.exceptions.ConnectorException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Metadata-aware access to a dynamically detected SQL table.
 *
 * <p>Centralizes case-insensitive column lookup, typed QueryDSL paths, value conversion,
 * schema-qualified DML, and predicates used by related-table readers and writers.</p>
 */
public final class SqlTableAccess {

    private final SqlBaseContext context;
    private final SqlTableInfo table;
    private final RelationalPathBase<?> path;
    private final Map<String, Path<?>> columnPaths = new LinkedHashMap<>();

    public SqlTableAccess(SqlBaseContext context, String tableName, String alias) {
        this(context, requireTable(context, tableName), alias);
    }

    public SqlTableAccess(
            SqlBaseContext context, String tableName, RelationalPathBase<?> path) {
        this.context = context;
        this.table = requireTable(context, tableName);
        this.path = path;
    }

    private SqlTableAccess(SqlBaseContext context, SqlTableInfo table, String alias) {
        this.context = context;
        this.table = table;
        var schema = table.getSchema();
        if (schema != null && (schema.isBlank() || "null".equalsIgnoreCase(schema))) {
            schema = null;
        }
        this.path = new RelationalPathBase<>(
                Object.class, PathMetadataFactory.forVariable(alias), schema, table.getName());
    }

    public SqlTableInfo metadata() {
        return table;
    }

    public RelationalPathBase<?> path() {
        return path;
    }

    public String actualColumn(String columnName) {
        var column = column(columnName);
        if (column == null) {
            throw new ConnectorException(
                    "Column " + columnName + " was not detected in " + table.getName());
        }
        return column.getName();
    }

    public SqlColumnMeta column(String columnName) {
        return table.getColumns().stream()
                .filter(candidate -> candidate.getName().equalsIgnoreCase(columnName))
                .findFirst()
                .orElse(null);
    }

    public Path<?> columnPath(String columnName) {
        var key = columnName.toLowerCase(Locale.ROOT);
        var existing = columnPaths.get(key);
        if (existing != null) {
            return existing;
        }
        var column = column(columnName);
        Path<?> result;
        if (column != null && column.getValueMapping() != null) {
            result = column.getValueMapping().pathFor(path, column.getName());
        } else {
            result = Expressions.path(Object.class, path, actualColumn(columnName));
        }
        columnPaths.put(key, result);
        return result;
    }

    public Object value(Tuple row, String columnName) {
        return row.get(columnPath(columnName));
    }

    public Object toWireValue(String columnName, Object value) {
        var column = column(columnName);
        if (column == null || column.getValueMapping() == null || value == null) {
            return value;
        }
        var mapping = column.getValueMapping();
        if (mapping.primaryWireType().isInstance(value)) {
            return value;
        }
        if (value instanceof String stringValue) {
            return parse(stringValue, mapping.primaryWireType());
        }
        return mapping.toWireValue(value);
    }

    public Object toConnIdValue(String columnName, Object value) {
        var column = column(columnName);
        if (value == null || column == null || column.getValueMapping() == null) {
            return value;
        }
        var mapping = column.getValueMapping();
        var wireValue = value;
        if (!mapping.primaryWireType().isInstance(value) && value instanceof Number) {
            wireValue = parse(value.toString(), mapping.primaryWireType());
        }
        return mapping.toConnIdValue(wireValue);
    }

    public BooleanExpression predicate(Map<String, Object> criteria) {
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("A SQL predicate requires at least one criterion");
        }
        BooleanExpression result = null;
        for (var entry : criteria.entrySet()) {
            var current = equal(columnPath(entry.getKey()), entry.getValue());
            result = result == null ? current : result.and(current);
        }
        return result;
    }

    public BooleanExpression matchingAny(Collection<? extends Map<String, Object>> alternatives) {
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("A SQL predicate requires at least one alternative");
        }
        BooleanExpression result = null;
        for (var alternative : alternatives) {
            var current = predicate(alternative);
            result = result == null ? current : result.or(current);
        }
        return result;
    }

    public void insert(SqlConnection connection, Map<String, Object> assignments) {
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException("No values supplied for table " + table.getName());
        }
        var insert = new SQLInsertClause(
                connection.getConnection(), context.getSqlTemplates(), path);
        assignments.forEach((column, value) -> set(insert, columnPath(column), value));
        insert.execute();
    }

    public long delete(SqlConnection connection, Map<String, Object> criteria) {
        return new SQLDeleteClause(connection.getConnection(), context.getSqlTemplates(), path)
                .where(predicate(criteria))
                .execute();
    }

    public boolean exists(SqlConnection connection, Map<String, Object> criteria) {
        return connection.newQuery()
                .select(Expressions.ONE)
                .from(path)
                .where(predicate(criteria))
                .fetchFirst() != null;
    }

    private static SqlTableInfo requireTable(SqlBaseContext context, String tableName) {
        var table = context.findTableInfo(tableName);
        if (table == null) {
            throw new ConnectorException("No detected metadata for related table " + tableName);
        }
        return table;
    }

    private static Object parse(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        }
        if (targetType == BigInteger.class) {
            return new BigInteger(value);
        }
        if (targetType == BigDecimal.class) {
            return new BigDecimal(value);
        }
        if (targetType == Integer.class) {
            return Integer.valueOf(value);
        }
        if (targetType == Long.class) {
            return Long.valueOf(value);
        }
        if (targetType == Short.class) {
            return Short.valueOf(value);
        }
        if (targetType == Byte.class) {
            return Byte.valueOf(value);
        }
        if (targetType == Double.class) {
            return Double.valueOf(value);
        }
        if (targetType == Float.class) {
            return Float.valueOf(value);
        }
        if (targetType == Boolean.class) {
            return Boolean.valueOf(value);
        }
        return value;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static BooleanExpression equal(Path<?> path, Object value) {
        var expression = (SimpleExpression) path;
        return value == null ? expression.isNull() : expression.eq(value);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void set(SQLInsertClause insert, Path<?> path, Object value) {
        if (value == null) {
            insert.setNull((Path) path);
        } else {
            insert.set((Path) path, value);
        }
    }
}
