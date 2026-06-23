/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

/**
 * PostgreSQL SQL dialect implementation.
 * Supports RETURNING clause for INSERT/UPDATE/DELETE operations.
 */
public class PostgreSqlDialect implements SqlDialect {

    @Override
    public String getId() {
        return "postgresql";
    }

    @Override
    public String normalizeParameters(String sql) {
        return sql;
    }

    @Override
    public String toSql(SqlStatement statement) {
        StringBuilder sql = new StringBuilder();
        if (!statement.getInsertValues().isEmpty()) {
            sql.append("INSERT INTO ").append(statement.getTableName()).append(" (");
            sql.append(String.join(", ", statement.getInsertValues().keySet())).append(") VALUES (");
            sql.append(String.join(", ", generatePlaceholders(statement.getInsertValues().size()))).append(")");
            sql.append(" RETURNING *");
        } else if (!statement.getUpdateValues().isEmpty()) {
            sql.append("UPDATE ").append(statement.getTableName()).append(" SET ");
            sql.append(statement.getUpdateValues().keySet().stream()
                    .map(k -> k + " = " + PLACEHOLDER).collect(java.util.stream.Collectors.joining(", ")));
            if (statement.getWhereClause() != null) {
                sql.append(" WHERE ").append(statement.getWhereClause());
            }
            sql.append(" RETURNING *");
        } else if (!statement.getInsertValues().isEmpty() && statement.getWhereClause() != null) {
            sql.append("DELETE FROM ").append(statement.getTableName());
            if (statement.getWhereClause() != null) {
                sql.append(" WHERE ").append(statement.getWhereClause());
            }
            sql.append(" RETURNING *");
        } else {
            sql.append("SELECT ");
            if (statement.getSelectColumns().isEmpty()) {
                sql.append("*");
            } else {
                sql.append(String.join(", ", statement.getSelectColumns()));
            }
            sql.append(" FROM ").append(statement.getTableName());
            if (statement.getWhereClause() != null) {
                sql.append(" WHERE ").append(statement.getWhereClause());
            }
            if (!statement.getOrderBy().isEmpty()) {
                sql.append(" ORDER BY ").append(String.join(", ", statement.getOrderBy()));
            }
            if (statement.getLimit() != null) {
                sql.append(" LIMIT ").append(statement.getLimit());
            }
            if (statement.getOffset() != null) {
                sql.append(" OFFSET ").append(statement.getOffset());
            }
        }
        return sql.toString();
    }
}