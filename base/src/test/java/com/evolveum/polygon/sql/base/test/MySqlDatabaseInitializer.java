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

/** Initializes the external MySQL instance used by opt-in integration tests. */
public final class MySqlDatabaseInitializer implements AutoCloseable {

    public static final String JDBC_URL = setting(
            "sql.test.mysql.url", "SQL_TEST_MYSQL_URL",
            "jdbc:mysql://localhost:3308/connector_sql?allowPublicKeyRetrieval=true&useSSL=false");
    public static final String USERNAME = setting(
            "sql.test.mysql.username", "SQL_TEST_MYSQL_USERNAME", "connector");
    public static final String PASSWORD = setting(
            "sql.test.mysql.password", "SQL_TEST_MYSQL_PASSWORD", "connector123");

    private static final String SCHEMA_RESOURCE = "mysql/basic/schema.sql";

    private final Connection connection;

    private MySqlDatabaseInitializer() throws SQLException {
        connection = DriverManager.getConnection(JDBC_URL, USERNAME, PASSWORD);
    }

    public static MySqlDatabaseInitializer create() throws SQLException {
        return new MySqlDatabaseInitializer();
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
