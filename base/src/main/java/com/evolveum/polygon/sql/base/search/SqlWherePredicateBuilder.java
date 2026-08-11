/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpressionBase;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.sql.RelationalPathBase;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit predicate builder for SQL WHERE clauses used in the built-in search.
 * 
 * <p>Uses explicit method calls (not Groovy operators):</p>
 * <pre>{@code
 * // Method-based syntax (explicit, no operator overloading):
 * where { e ->
 *     e.col("LEGACY").eq(false)
 *     e.col("STATUS").ne("deleted")
 *     e.col("USERNAME").ne(null)
 * }
 * }</pre>
 *
 * <p>Groovy's {@code ==} and {@code !=} operators map to Java {@code equals()} and
 * {@code !equals()} — they cannot dispatch to {@code eq()} or {@code ne()}.
 * Always use explicit method calls for correct semantics.</p>
 */
public class SqlWherePredicateBuilder {
    private final RelationalPathBase<?> tablePath;
    private final SqlBaseContext context;
    private final List<BooleanExpression> predicates = new ArrayList<>();

    /** Cached column references: avoids repeated metadata lookups. */
    private final Map<String, SqlColumn> columns = new HashMap<>();

    SqlWherePredicateBuilder(RelationalPathBase<?> tablePath,
                             SqlBaseContext context) {
        this.tablePath = tablePath;
        this.context = context;
    }

    /** Explicit method: {@code e.col('name')} or {@code e.column('name')} */
    @SuppressWarnings("unused")
    public SqlColumn col(String name) {
        return columns.computeIfAbsent(name, n -> {
            var col = findColumnMeta(n);
            if (col == null) {
                throw new IllegalArgumentException("Column not found: " + n);
            }
            return new SqlColumn(tablePath, n, col.getJavaType());
        });
    }

    /** Explicit method: {@code e.column('name')} — alias for {@code col()} */
    @SuppressWarnings("unused")
    public SqlColumn column(String name) {
        return col(name);
    }

    /** Explicit method: {@code e.add(predicate)} — adds a condition directly */
    @SuppressWarnings("unused")
    public SqlWherePredicateBuilder add(BooleanExpression predicate) {
        predicates.add(predicate);
        return this;
    }

    /** Combines all registered predicates with AND */
    public BooleanExpression build() {
        return predicates.stream().reduce(BooleanExpression::and).orElse(null);
    }

    private SqlColumnMeta findColumnMeta(String name) {
        var tables = context.getTableInfos();
        if (tables == null || tables.isEmpty()) return null;

        var tableName = tablePath.getTableName();
        for (Map.Entry<String, SqlTableInfo> entry : tables.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(tableName)) {
                for (SqlColumnMeta c : entry.getValue().getColumns()) {
                    if (c.getName().equalsIgnoreCase(name)) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    /**
     * QueryDSL column wrapper. Provides type-safe eq/ne operations.
     */
    @SuppressWarnings("rawtypes")
    public class SqlColumn {
        private final String columnName;
        private final ComparableExpressionBase path;
        private final SqlWherePredicateBuilder builder;

        SqlColumn(RelationalPathBase<?> tpath, String colName, java.lang.reflect.Type javaType) {
            this.columnName = colName;
            this.path = createComparablePath(tpath, colName, javaType);
            this.builder = SqlWherePredicateBuilder.this;
        }

    @SuppressWarnings("unused")
    public SqlWherePredicateBuilder eq(Object value) {
        BooleanExpression predicate;
        if (value == null) {
            predicate = path.isNull();
        } else {
            predicate = path.eq(value);
        }
        builder.predicates.add(predicate);
        return builder;
    }

    @SuppressWarnings("unused")
    public SqlWherePredicateBuilder ne(Object value) {
        var predicate = path.ne(value);
        builder.predicates.add(predicate);
        return builder;
    }

    @SuppressWarnings("unused")
    public OrderSpecifier<?> asc() {
            return new OrderSpecifier<>(Order.ASC, path);
        }

        @SuppressWarnings("unused")
        public OrderSpecifier<?> desc() {
            return new OrderSpecifier<>(Order.DESC, path);
        }

        @Override
        public String toString() {
            return path.toString();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ComparableExpressionBase createComparablePath(RelationalPathBase<?> table, String col,
                                                                  java.lang.reflect.Type javaType) {
        Class<?> clazz = guessClass(javaType);

        if (clazz == null || String.class.isAssignableFrom(clazz)) {
            return Expressions.stringPath(table, col);
        }
        if (Boolean.class == clazz || boolean.class == clazz) {
            return Expressions.booleanPath(table, col);
        }
        if (Integer.class == clazz || int.class == clazz) {
            return Expressions.numberPath(Integer.class, table, col);
        }
        if (Long.class == clazz || long.class == clazz) {
            return Expressions.numberPath(Long.class, table, col);
        }
        if (Short.class == clazz || short.class == clazz) {
            return Expressions.numberPath(Short.class, table, col);
        }
        if (BigInteger.class.isAssignableFrom(clazz)) {
            return Expressions.numberPath(BigInteger.class, table, col);
        }
        if (BigDecimal.class.isAssignableFrom(clazz)) {
            return Expressions.numberPath(BigDecimal.class, table, col);
        }
        if (Double.class == clazz || double.class == clazz) {
            return Expressions.numberPath(Double.class, table, col);
        }
        if (Float.class == clazz || float.class == clazz) {
            return Expressions.numberPath(Float.class, table, col);
        }
        if (ZonedDateTime.class.isAssignableFrom(clazz) || 
                Timestamp.class.isAssignableFrom(clazz)) {
            return Expressions.dateTimePath(ZonedDateTime.class, table, col);
        }
        if (Date.class.isAssignableFrom(clazz)) {
            return Expressions.dateTimePath(Date.class, table, col);
        }
        if (LocalTime.class.isAssignableFrom(clazz)) {
            return Expressions.stringPath(table, col);
        }
        return Expressions.stringPath(table, col);
    }

    private static Class<?> guessClass(java.lang.reflect.Type type) {
        if (type instanceof Class<?> class1) {
            return class1;
        }
        return null;
    }
}
