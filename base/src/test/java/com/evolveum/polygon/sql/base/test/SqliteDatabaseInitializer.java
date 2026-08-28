/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/** Initializes an embedded file-backed SQLite database for integration tests. */
public final class SqliteDatabaseInitializer implements AutoCloseable {

    private static final String SCHEMA_RESOURCE = "sqlite/basic/schema.sql";

    private final Path databaseFile;
    private final String jdbcUrl;
    private final Connection connection;

    private SqliteDatabaseInitializer() throws IOException, SQLException {
        databaseFile = Files.createTempFile("connector-sql-sqlite-", ".db");
        jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        connection = DriverManager.getConnection(jdbcUrl);
    }

    public static SqliteDatabaseInitializer create() throws IOException, SQLException {
        return new SqliteDatabaseInitializer();
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

    public String jdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public void close() throws IOException, SQLException {
        connection.close();
        Files.deleteIfExists(databaseFile);
    }
}
