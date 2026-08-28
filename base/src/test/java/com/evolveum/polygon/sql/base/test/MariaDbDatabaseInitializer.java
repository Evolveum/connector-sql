/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/** Initializes the external MariaDB instance used by opt-in integration tests. */
public final class MariaDbDatabaseInitializer implements AutoCloseable {

    public static final String JDBC_URL = setting(
            "sql.test.mariadb.url", "SQL_TEST_MARIADB_URL",
            "jdbc:mariadb://localhost:3307/connector_sql");
    public static final String USERNAME = setting(
            "sql.test.mariadb.username", "SQL_TEST_MARIADB_USERNAME", "connector");
    public static final String PASSWORD = setting(
            "sql.test.mariadb.password", "SQL_TEST_MARIADB_PASSWORD", "connector123");

    private static final String SCHEMA_RESOURCE = "mariadb/basic/schema.sql";

    private final Connection connection;

    private MariaDbDatabaseInitializer() throws SQLException {
        connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
    }

    public static MariaDbDatabaseInitializer create() throws SQLException {
        return new MariaDbDatabaseInitializer();
    }

    public void init() throws IOException, SQLException {
        var stream = Objects.requireNonNull(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(SCHEMA_RESOURCE),
                "Resource not found: " + SCHEMA_RESOURCE);
        try (stream) {
            var sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (var statementSql : sql.split(";")) {
                if (statementSql.isBlank()) {
                    continue;
                }
                try (var statement = connection.createStatement()) {
                    statement.execute(statementSql);
                }
            }
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private static String setting(String property, String environment, String defaultValue) {
        var configured = System.getProperty(property);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        configured = System.getenv(environment);
        return configured != null && !configured.isBlank() ? configured : defaultValue;
    }
}
