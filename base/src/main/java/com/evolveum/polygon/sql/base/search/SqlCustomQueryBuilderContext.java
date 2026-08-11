/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.BooleanExpression;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * DSL context passed to custom query closures.
 * 
 * <p>Defines the query shape that SqlCustomSearchOperation reproduces at runtime.</p>
 * <pre>{@code
 * query { q ->
 *     def a = q.table('accounts', 'a')
 *     q.select(a.column('id'), a.column('username'))
 *      .from(a)
 *      .where(a.column('status').eq('active'))
 *      .orderBy(a.column('username').asc())
 * }
 * }</pre>
 */
@SuppressWarnings("unused")
public class SqlCustomQueryBuilderContext {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private final Filter filter;

    // Builder accumulators – populated by Groovy closure calls
    private final List<Path<?>> selectPaths = new ArrayList<>();
    private SqlTablePath fromTable;
    private final List<BooleanExpression> wherePredicates = new ArrayList<>();
    private final List<OrderSpecifier<?>> orderBys = new ArrayList<>();

    SqlCustomQueryBuilderContext(SqlBaseContext context,
                                 SqlObjectClassDefinition objectClass,
                                 Filter filter) {
        this.context = context;
        this.objectClass = objectClass;
        this.filter = filter;
    }

    /**
     * Reference to a SQL table. Column types are automatically resolved from schema metadata.
     * <p>Usage: {@code def t = q.table("accounts", "a")}</p>
     */
    public SqlTablePath table(String name, String alias) {
        return new SqlTablePath(context, name, alias);
    }

    /** Adds columns to SELECT clause. Returns this for chaining. */
    public SqlCustomQueryBuilderContext select(Path<?>... paths) {
        for (Path<?> p : paths) {
            selectPaths.add(p);
        }
        return this;
    }

    /** Groovy-friendly: adds columns from a list. Returns this for chaining. */
    public SqlCustomQueryBuilderContext select(Collection<Path<?>> paths) {
        selectPaths.addAll(paths);
        return this;
    }

    /** Adds columns to SELECT clause via SqlColumnRef. Returns this for chaining. */
    public SqlCustomQueryBuilderContext select(SqlColumnRef... columns) {
        for (SqlColumnRef col : columns) {
            selectPaths.add(col.getPath());
        }
        return this;
    }

    /** Sets the FROM clause table. Returns this for chaining. */
    public SqlCustomQueryBuilderContext from(SqlTablePath table) {
        this.fromTable = table;
        return this;
    }

    /** Adds a WHERE predicate. Returns this for chaining. */
    public SqlCustomQueryBuilderContext where(BooleanExpression predicate) {
        wherePredicates.add(predicate);
        return this;
    }

    /** Adds ORDER BY specifiers. Returns this for chaining. */
    public SqlCustomQueryBuilderContext orderBy(OrderSpecifier<?>... specs) {
        for (OrderSpecifier<?> s : specs) {
            orderBys.add(s);
        }
        return this;
    }

    /**
     * Returns the ConnId filter value (for single-value attributes).
     * <p>Example: {@code .where(a.column('username').eq(q.value()))}</p>
     * <p>Returns null if filter is null or composite.</p>
     */
    @SuppressWarnings("unused")
    public Object value() {
        return extractSingleValue(filter);
    }

    private static Object extractSingleValue(Filter f) {
        if (f == null) return null;
        if (f instanceof AttributeFilter af) {
            var attr = af.getAttribute();
            if (attr != null && !attr.getValue().isEmpty()) {
                return attr.getValue().getFirst();
            }
        }
        return null;
    }

    /**
     * Converts a ConnId value to a SQL wire value.
     * <p>Handles type conversion: String → VARCHAR, Boolean → BOOLEAN, etc.</p>
     */
    public Object sqlValue(Object v) {
        if (v instanceof String || v instanceof Number || v instanceof Boolean) {
            return v;
        }
        if (v instanceof ZonedDateTime zdt) {
            return Timestamp.from(zdt.toInstant());
        }
        if (v instanceof Instant instant) {
            return Timestamp.from(instant);
        }
        if (v instanceof Date date) {
            return date;
        }
        if (v instanceof byte[] bytes) {
            return bytes;
        }
        return v;
    }

    /** Returns the full ConnId filter (rarely needed). */
    public Filter getFilter() {
        return filter;
    }

    // ─── Accumulator access (runtime use) ──────────────────────────────

    /** Returns accumulated SELECT columns. */
    List<Path<?>> getSelectPathsInternal() {
        return new ArrayList<>(selectPaths);
    }

    /** Returns the FROM table. */
    SqlTablePath getFromTableInternal() {
        return fromTable;
    }

    /** Returns WHERE predicates. */
    List<BooleanExpression> getWherePredicatesInternal() {
        return new ArrayList<>(wherePredicates);
    }

    /** Returns ORDER BY specifiers. */
    List<OrderSpecifier<?>> getOrderBysInternal() {
        return new ArrayList<>(orderBys);
    }
}
