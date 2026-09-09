/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlObjectJoin;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.search.SqlJoinPredicateBuilder;
import com.evolveum.polygon.sql.base.search.SqlWherePredicateBuilder;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.sql.RelationalPathBase;
import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Schema DSL for one read-only LEFT JOIN; {@code left()} always denotes the root object table.
 * Each join must match at most one row per root UID (checked during search). Its {@code where}
 * restrictions belong to ON, preserving roots with no matching row. Missing/ambiguous foreign
 * keys, self-joins and cross-schema joins require explicit column equalities in {@code on}.
 * Prefixes affect exposed attribute names, not the independent internal SQL aliases.
 */
public final class SqlJoinBuilder {
    private String table;
    private String schema;
    private String prefix = "";
    private final Set<String> skipped = new LinkedHashSet<>();
    private Closure<?> on;
    private Closure<?> where;

    public void table(String value) { table = value; }
    public String table() { return table; }
    public void schema(String value) { schema = value; }
    public String schema() { return schema; }
    public void prefixAttributes(String value) { prefix = value; }
    public String attributeName(String column) { return prefix + column; }
    public void skipAttributes(String... names) {
        for (var name : names) {
            skipped.add(name.toLowerCase(Locale.ROOT));
        }
    }
    public boolean includes(String column) { return !skipped.contains(column.toLowerCase(Locale.ROOT)); }
    public void on(Closure<?> closure) { on = closure; }
    public void where(Closure<?> closure) { where = closure; }

    public SqlObjectJoin resolve(SqlTableInfo root, List<SqlTableInfo> tables, int index) {
        if (table == null || table.isBlank() || prefix == null) {
            throw new IllegalArgumentException("Join requires a table and a non-null attribute prefix");
        }
        var effectiveSchema = schema != null ? schema : root.getSchema();
        var matches = tables.stream().filter(candidate -> candidate.getName().equalsIgnoreCase(table)
                && (effectiveSchema == null || effectiveSchema.isBlank()
                || effectiveSchema.equalsIgnoreCase(candidate.getSchema()))).toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Join table " + table + " must resolve to exactly one detected table/view");
        }
        var target = matches.getFirst();
        for (var skippedColumn : skipped) {
            if (target.getColumns().stream().noneMatch(column -> column.getName().equalsIgnoreCase(skippedColumn))) {
                throw new IllegalArgumentException("Unknown skipped column " + skippedColumn + " in " + table);
            }
        }
        var leftPath = path(root, "o");
        var rightPath = path(target, "j" + index);
        var predicate = new SqlJoinPredicateBuilder(root, leftPath, target, rightPath);
        if (on != null) {
            ((Closure<?>) on.clone()).call(predicate);
        } else {
            inferOn(root, target, predicate);
        }
        var condition = predicate.build();
        if (where != null) {
            var restrictions = new SqlWherePredicateBuilder(rightPath, target);
            ((Closure<?>) where.clone()).call(restrictions);
            var filter = restrictions.build();
            if (filter != null) {
                condition = condition.and(filter);
            }
        }
        return new SqlObjectJoin(target, rightPath, condition);
    }

    private static RelationalPathBase<?> path(SqlTableInfo table, String alias) {
        var schema = table.getSchema();
        return new RelationalPathBase<>(Object.class, PathMetadataFactory.forVariable(alias),
                schema == null || schema.isBlank() ? null : schema, table.getName());
    }

    private static void inferOn(SqlTableInfo root, SqlTableInfo target, SqlJoinPredicateBuilder predicate) {
        // Column metadata does not retain the referenced schema: cross-schema joins need explicit ON.
        if (!Objects.equals(root.getSchema(), target.getSchema())) {
            throw new IllegalArgumentException("Cross-schema join requires explicit on");
        }
        if (root.getName().equalsIgnoreCase(target.getName())) {
            throw new IllegalArgumentException("Self-join requires explicit on to select its direction");
        }
        var candidates = new ArrayList<List<Key>>();
        collectKeys(target, root, false, candidates);
        collectKeys(root, target, true, candidates);
        if (candidates.size() != 1) {
            throw new IllegalArgumentException("Join " + root.getName() + " -> " + target.getName()
                    + " requires explicit on: expected one unambiguous foreign key");
        }
        for (var key : candidates.getFirst()) {
            predicate.left().column(key.left()).eq(predicate.right().column(key.right()));
        }
    }

    private static void collectKeys(SqlTableInfo from, SqlTableInfo to, boolean fromLeft,
                                    List<List<Key>> candidates) {
        Map<String, List<Key>> constraints = new LinkedHashMap<>();
        for (SqlColumnMeta column : from.getColumns()) {
            if (column.getForeignKeyName() != null && column.getReferencedColumn() != null
                    && to.getName().equalsIgnoreCase(column.getReferencedTable())) {
                constraints.computeIfAbsent(column.getForeignKeyName(), ignored -> new ArrayList<>())
                        .add(fromLeft ? new Key(column.getName(), column.getReferencedColumn())
                                : new Key(column.getReferencedColumn(), column.getName()));
            }
        }
        candidates.addAll(constraints.values());
    }

    private record Key(String left, String right) { }
}
