/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

/**
 * Base interface for SQL dialect implementations.
 * Provides dialect-specific SQL generation and normalization.
 */
public interface SqlDialect {

    String PLACEHOLDER = "?";

    String getId();

    String normalizeParameters(String sql);

    String toSql(SqlStatement statement);

default String generateInsertWithReturns(String tableName, 
                                               String[] columns, 
                                               int returnCount) {
        return """
            INSERT INTO %s (%s) VALUES (%s)\
            """.formatted(tableName, 
                         String.join(", ", columns),
                         String.join(", ", generatePlaceholders(columns.length)));
    }

    default String[] generatePlaceholders(int count) {
        String[] placeholders = new String[count];
        for (int i = 0; i < count; i++) {
            placeholders[i] = PLACEHOLDER;
        }
        return placeholders;
    }

    static SqlDialect detectFromUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return new StandardSqlDialect();
        }
        if (jdbcUrl.startsWith("jdbc:postgresql://")) {
            return new PostgreSqlDialect();
        } else if (jdbcUrl.startsWith("jdbc:mysql://")) {
            return new MySqlDialect();
        } else if (jdbcUrl.startsWith("jdbc:oracle://")) {
            return new OracleSqlDialect();
        } else if (jdbcUrl.startsWith("jdbc:sqlite://")) {
            return new SqliteDialect();
        }
        return new StandardSqlDialect();
    }

    static SqlDialect create(String dialectName) {
        if (dialectName == null || "auto".equalsIgnoreCase(dialectName)) {
            return new StandardSqlDialect();
        }
        switch (dialectName.toLowerCase()) {
            case "postgresql":
            case "postgres":
                return new PostgreSqlDialect();
            case "mysql":
            case "mariadb":
                return new MySqlDialect();
            case "oracle":
                return new OracleSqlDialect();
            case "sqlite":
                return new SqliteDialect();
            default:
                return new StandardSqlDialect();
        }
    }
}