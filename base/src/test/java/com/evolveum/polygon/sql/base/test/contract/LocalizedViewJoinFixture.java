/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.test.contract;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Portable fixture created only for the localized-view tests, on their existing test database. */
final class LocalizedViewJoinFixture implements AutoCloseable {
    private final Connection connection;
    private final boolean upperCaseIdentifiers;

    LocalizedViewJoinFixture(Connection connection) throws Exception {
        this.connection = connection;
        upperCaseIdentifiers = connection.getMetaData().storesUpperCaseIdentifiers();
        try (var stream = Objects.requireNonNull(getClass().getClassLoader()
                .getResourceAsStream("database/localized-view-joins.sql"))) {
            var script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (var sql : script.split(";")) {
                if (!sql.isBlank()) {
                    execute(sql);
                }
            }
        } catch (Exception e) {
            try {
                close();
            } catch (Exception cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    String identifier(String name) {
        return upperCaseIdentifiers ? name.toUpperCase(Locale.ROOT) : name;
    }

    void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Override
    public void close() throws SQLException {
        SQLException failure = null;
        for (var object : List.of(
                "VIEW contract_unit_label_v", "VIEW contract_unit_type_v", "VIEW contract_unit_v",
                "TABLE contract_unit_label", "TABLE contract_unit_type", "TABLE contract_unit")) {
            try {
                execute("DROP " + object);
            } catch (SQLException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
