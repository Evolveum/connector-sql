/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import java.util.List;
import java.util.Map;

/**
 * Fluent builder for SQL statements with dialect support.
 * Chainable API for constructing complex SQL queries.
 */
public class SqlStatementBuilder {

    private final SqlDialect dialect;
    private SqlStatement.Builder builder;

    public SqlStatementBuilder(SqlDialect dialect) {
        this.dialect = dialect;
        this.builder = SqlStatement.builder();
    }

    public SqlStatementBuilder from(String tableName) {
        builder.tableName(tableName);
        return this;
    }

    public SqlStatementBuilder select(String... columns) {
        builder.selectColumns(List.of(columns));
        return this;
    }

    public SqlStatementBuilder select(List<String> columns) {
        builder.selectColumns(columns);
        return this;
    }

    public SqlStatementBuilder insert(Map<String, Object> values) {
        builder.insertAll(values);
        return this;
    }

    public SqlStatementBuilder update(Map<String, Object> values) {
        builder.updateAll(values);
        return this;
    }

    public SqlStatementBuilder where(String whereClause, Object... params) {
        String normalizedWhere = dialect.normalizeParameters(whereClause);
        builder.where(normalizedWhere);
        return this;
    }

    public SqlStatementBuilder orderBy(String... columns) {
        builder.orderBy(List.of(columns));
        return this;
    }

    public SqlStatementBuilder orderBy(List<String> columns) {
        builder.orderBy(columns);
        return this;
    }

    public SqlStatementBuilder limit(Integer limit) {
        builder.limit(limit);
        return this;
    }

    public SqlStatementBuilder offset(Integer offset) {
        builder.offset(offset);
        return this;
    }

    public SqlStatement buildSelect() {
        return builder.build();
    }

    public SqlStatement buildInsert() {
        return builder.build();
    }

    public SqlStatement buildUpdate() {
        return builder.build();
    }

    public SqlStatement buildDelete() {
        return builder.build();
    }

    public String toSql(SqlStatement statement) {
        return dialect.toSql(statement);
    }
}