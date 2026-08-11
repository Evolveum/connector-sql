/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.search;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Path;
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

/**
 * Wraps a QueryDSL column path for use in custom SELECT, WHERE, ORDER BY clauses.
 * 
 * <p>Created via {@code SqlTablePath.column(name)} — column Java type is inferred from
 * schema-detected metadata.</p>
 */
    @SuppressWarnings("rawtypes")
    public class SqlColumnRef {

    private final ComparableExpressionBase columnPath;
    private final SqlTablePath tablePath;

    SqlColumnRef(SqlTablePath tablePath, String name, java.lang.reflect.Type javaType) {
        this.tablePath = tablePath;
        this.columnPath = resolveColumn(tablePath.tableRef(), name, javaType);
    }

    SqlTablePath tablePath() {
        return tablePath;
    }

    /**
     * Equality predicate: {@code t.column('status').eq('active')}
     */
    @SuppressWarnings("unused")
    public BooleanExpression eq(Object value) {
        if (value == null) {
            return columnPath.isNull();
        }
        return columnPath.eq(value);
    }

    /**
     * Not-equals predicate: {@code t.column('status').ne('deleted')}
     */
    @SuppressWarnings("unused")
    public BooleanExpression ne(Object value) {
        return columnPath.ne(value);
    }

    /** ASC order specifier: {@code t.column('username').asc()} */
    @SuppressWarnings("unused")
    public OrderSpecifier<?> asc() {
        return columnPath.asc();
    }

    /** DESC order specifier: {@code t.column('username').desc()} */
    @SuppressWarnings("unused")
    public OrderSpecifier<?> desc() {
        return columnPath.desc();
    }

    /** Returns the underlying QueryDSL path for SELECT and other uses. */
    @SuppressWarnings({"unused", "unchecked"})
    public Path<?> getPath() {
        return (Path<?>) (Path) columnPath;
    }

    @Override
    public String toString() {
        return columnPath.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ComparableExpressionBase resolveColumn(RelationalPathBase<?> table,
                                                           String name,
                                                           java.lang.reflect.Type javaType) {
        Class<?> clazz = javaType instanceof Class<?> c ? c : null;

        if (clazz == null || String.class.isAssignableFrom(clazz)) {
            return Expressions.stringPath(table, name);
        }
        if (Boolean.class == clazz || boolean.class == clazz) {
            return Expressions.booleanPath(table, name);
        }
        if (Integer.class == clazz || int.class == clazz) {
            return Expressions.numberPath(Integer.class, table, name);
        }
        if (Long.class == clazz || long.class == clazz) {
            return Expressions.numberPath(Long.class, table, name);
        }
        if (Short.class == clazz || short.class == clazz) {
            return Expressions.numberPath(Short.class, table, name);
        }
        if (BigInteger.class.isAssignableFrom(clazz)) {
            return Expressions.numberPath(BigInteger.class, table, name);
        }
        if (BigDecimal.class.isAssignableFrom(clazz)) {
            return Expressions.numberPath(BigDecimal.class, table, name);
        }
        if (Double.class == clazz || double.class == clazz) {
            return Expressions.numberPath(Double.class, table, name);
        }
        if (Float.class == clazz || float.class == clazz) {
            return Expressions.numberPath(Float.class, table, name);
        }
        if (ZonedDateTime.class.isAssignableFrom(clazz) ||
                Timestamp.class.isAssignableFrom(clazz)) {
            return Expressions.dateTimePath(ZonedDateTime.class, table, name);
        }
        if (Date.class.isAssignableFrom(clazz)) {
            return Expressions.dateTimePath(Date.class, table, name);
        }
        if (LocalTime.class.isAssignableFrom(clazz)) {
            return Expressions.stringPath(table, name);
        }
        return Expressions.stringPath(table, name);
    }
}
