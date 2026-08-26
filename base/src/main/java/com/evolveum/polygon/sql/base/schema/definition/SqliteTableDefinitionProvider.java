/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.definition;

import com.evolveum.polygon.sql.base.SqlDatabase;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** Reads native SQLite table and view definitions from {@code sqlite_schema}. */
public final class SqliteTableDefinitionProvider implements SqlTableDefinitionProvider {

    private static final String SELECT_DEFINITION =
            "SELECT sql FROM sqlite_schema WHERE type = ? AND name = ?";

    @Override
    public SqlDatabase database() {
        return SqlDatabase.SQLITE;
    }

    @Override
    public Optional<String> readDefinition(Connection connection, String catalog, String schema,
            String name, String tableType) throws SQLException {
        var objectType = "VIEW".equalsIgnoreCase(tableType) ? "view" : "table";
        try (var statement = connection.prepareStatement(SELECT_DEFINITION)) {
            statement.setString(1, objectType);
            statement.setString(2, name);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                var definition = result.getString(1);
                return definition == null || definition.isBlank()
                        ? Optional.empty()
                        : Optional.of(definition.strip());
            }
        }
    }
}
