/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.definition;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.SqlDatabase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** Reads native PostgreSQL table and view definitions with {@code pg_dump}. */
public final class PostgreSqlTableDefinitionProvider implements SqlTableDefinitionProvider {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);

    private final SqlConnectorConfiguration configuration;

    public PostgreSqlTableDefinitionProvider(SqlConnectorConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public SqlDatabase database() {
        return SqlDatabase.POSTGRESQL;
    }

    @Override
    public Optional<String> readDefinition(Connection connection, String catalog, String schema,
            String name, String tableType) throws SQLException {
        var executable = configuration.getPgDumpPath();
        if (executable == null || executable.isBlank()) {
            return Optional.empty();
        }

        var command = new ArrayList<String>();
        command.add(executable);
        command.add("--schema-only");
        command.add("--no-owner");
        command.add("--no-privileges");
        command.add("--no-password");
        command.add("--quote-all-identifiers");
        command.add("--encoding=UTF8");
        command.add("--dbname=" + postgresUrl(connection));
        command.add("--username=" + configuration.getUsername());
        command.add("--table=" + tablePattern(schema, name));

        var processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("LC_ALL", "C");
        addPassword(processBuilder);

        final Process process;
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new SQLException("Could not start pg_dump", e);
        }

        try {
            var standardOutput = readAsync(process.getInputStream());
            var standardError = readAsync(process.getErrorStream());
            if (!process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new SQLException("pg_dump timed out after " + PROCESS_TIMEOUT.toSeconds() + " seconds");
            }

            var output = await(standardOutput);
            var error = await(standardError);
            if (process.exitValue() != 0) {
                throw new SQLException("pg_dump failed with exit code " + process.exitValue()
                        + (error.isBlank() ? "" : ": " + error.strip()));
            }
            return output.isBlank() ? Optional.empty() : Optional.of(output.strip());
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for pg_dump", e);
        }
    }

    private void addPassword(ProcessBuilder processBuilder) {
        if (configuration.getPassword() == null) {
            return;
        }
        var password = new GuardedStringAccessor();
        configuration.getPassword().access(password);
        processBuilder.environment().put("PGPASSWORD", password.getClearString());
    }

    private static String postgresUrl(Connection connection) throws SQLException {
        var jdbcUrl = connection.getMetaData().getURL();
        var prefix = "jdbc:postgresql:";
        if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
            throw new SQLException("Unsupported PostgreSQL JDBC URL");
        }
        return "postgresql:" + jdbcUrl.substring(prefix.length());
    }

    private static String tablePattern(String schema, String name) {
        return schema == null || schema.isBlank()
                ? quotePatternIdentifier(name)
                : quotePatternIdentifier(schema) + "." + quotePatternIdentifier(name);
    }

    private static String quotePatternIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static CompletableFuture<String> readAsync(java.io.InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static String await(CompletableFuture<String> output) throws SQLException {
        try {
            return output.join();
        } catch (CompletionException e) {
            throw new SQLException("Could not read pg_dump output", e.getCause());
        }
    }
}
