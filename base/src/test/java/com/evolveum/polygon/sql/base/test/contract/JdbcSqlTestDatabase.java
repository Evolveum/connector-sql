/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.test.contract;

import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.SqlDatabase;
import org.identityconnectors.common.security.GuardedString;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Objects;

/** JDBC-backed implementation shared by all database fixtures. */
final class JdbcSqlTestDatabase implements SqlTestDatabase {

    private static final String CONTRACT_FILTER = "(?i)^contract_.*$";

    private final SqlDatabase database;
    private final DatabaseCapabilities capabilities;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String schemaResource;
    private final List<String> beforeDrop;
    private final List<String> dropStatements;
    private final List<String> afterDrop;
    private final AutoCloseable closeAction;

    JdbcSqlTestDatabase(
            SqlDatabase database,
            DatabaseCapabilities capabilities,
            String jdbcUrl,
            String username,
            String password,
            String schemaResource,
            List<String> beforeDrop,
            List<String> dropStatements,
            List<String> afterDrop,
            AutoCloseable closeAction) {
        this.database = database;
        this.capabilities = capabilities;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.schemaResource = schemaResource;
        this.beforeDrop = beforeDrop;
        this.dropStatements = dropStatements;
        this.afterDrop = afterDrop;
        this.closeAction = closeAction;
    }

    @Override
    public SqlDatabase database() {
        return database;
    }

    @Override
    public DatabaseCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public SqlConnectorConfiguration configuration(boolean developmentMode) {
        var configuration = new SqlConnectorConfiguration();
        configuration.setJdbcUrl(jdbcUrl);
        configuration.setUsername(username);
        configuration.setPassword(new GuardedString(password.toCharArray()));
        configuration.setPoolSize(5);
        configuration.setConnectionTimeout(10000);
        configuration.setValidateConnectionOnBorrow(true);
        configuration.setScanTables(true);
        configuration.setScanViews(true);
        configuration.setScanTableFilter(CONTRACT_FILTER);
        configuration.setScanViewFilter(CONTRACT_FILTER);
        configuration.setDevelopmentMode(developmentMode);
        if (database == SqlDatabase.POSTGRESQL) {
            configuration.setPgDumpPath(System.getProperty("sql.test.postgresql.pgDumpPath", ""));
        }
        return configuration;
    }

    @Override
    public void initializeSchema() throws Exception {
        try (var connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            executeAll(connection, beforeDrop, false);
            executeAll(connection, dropStatements, true);
            executeAll(connection, afterDrop, false);
            executeScript(connection, schemaResource);
        }
    }

    @Override
    public void close() throws Exception {
        closeAction.close();
    }

    private static void executeAll(Connection connection, List<String> statements, boolean ignoreFailure)
            throws Exception {
        for (var sql : statements) {
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
            } catch (Exception e) {
                if (!ignoreFailure) {
                    throw e;
                }
            }
        }
    }

    private static void executeScript(Connection connection, String resourcePath) throws Exception {
        var classLoader = Thread.currentThread().getContextClassLoader();
        try (var stream = Objects.requireNonNull(
                classLoader.getResourceAsStream(resourcePath), "Resource not found: " + resourcePath)) {
            var script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (var sql : script.split(";")) {
                if (sql.isBlank()) {
                    continue;
                }
                try (var statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        }
    }
}
