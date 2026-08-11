/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.search;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadataFactory;
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
    private final String tableName;
    private final String alias;
    private RelationalPathBase<?> tableRef;

    SqlColumnRef(SqlTablePath tablePath, String name, java.lang.reflect.Type javaType) {
        this.tableName = tablePath.getTableName();
        this.alias = tablePath.getAlias();
        this.columnPath = resolveColumn(name, javaType);
    }

    private RelationalPathBase<?> tableRef() {
        if (tableRef == null) {
            tableRef = new RelationalPathBase<>(
                    Object.class,
                    PathMetadataFactory.forVariable(alias),
                    null,
                    tableName);
        }
        return tableRef;
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

    @SuppressWarnings("rawtypes")
    private static ComparableExpressionBase resolveColumn(String name,
                                                          java.lang.reflect.Type javaType) {
        // Build QueryDSL variable name for this column: "alias.columnName"
        Class<?> clazz = javaType instanceof Class<?> c ? c : null;

        if (clazz == null || String.class.isAssignableFrom(clazz)) {
            return Expressions.stringPath(name);
        }
        if (Boolean.class == clazz || boolean.class == clazz) {
            return Expressions.booleanPath(name);
        }
        if (Integer.class == clazz || int.class == clazz) {
            return Expressions.numberPath(Integer.class, name);
        }
        if (Long.class == clazz || long.class == clazz) {
            return Expressions.numberPath(Long.class, name);
        }
        if (Short.class == clazz || short.class == clazz) {
            return Expressions.numberPath(Short.class, name);
        }
        if (BigInteger.class.isAssignableFrom(clazz)) {
            return Expressions.numberPath(BigInteger.class, name);
        }
        if (BigDecimal.class.isAssignableFrom(clazz)) {
            return Expressions.numberPath(BigDecimal.class, name);
        }
        if (Double.class == clazz || double.class == clazz) {
            return Expressions.numberPath(Double.class, name);
        }
        if (Float.class == clazz || float.class == clazz) {
            return Expressions.numberPath(Float.class, name);
        }
        if (ZonedDateTime.class.isAssignableFrom(clazz) ||
                Timestamp.class.isAssignableFrom(clazz)) {
            return Expressions.dateTimePath(ZonedDateTime.class, name);
        }
        if (Date.class.isAssignableFrom(clazz)) {
            return Expressions.dateTimePath(Date.class, name);
        }
        if (LocalTime.class.isAssignableFrom(clazz)) {
            return Expressions.stringPath(name);
        }
        return Expressions.stringPath(name);
    }
}
