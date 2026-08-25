/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.definition;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** Reads H2 table and view definitions using {@code SCRIPT NODATA TABLE}. */
public final class H2SqlTableDefinitionProvider implements SqlTableDefinitionProvider {

    @Override
    public boolean supports(String databaseProductName) {
        return "H2".equalsIgnoreCase(databaseProductName);
    }

    @Override
    public Optional<String> readDefinition(Connection connection, String catalog, String schema,
            String name, String tableType) throws SQLException {
        var qualifiedName = schema == null || schema.isBlank()
                ? quote(connection, name)
                : quote(connection, schema) + "." + quote(connection, name);
        var sql = "SCRIPT NODATA TABLE " + qualifiedName;
        var definition = new StringBuilder();
        boolean objectDefinitionStarted = false;

        try (var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            while (result.next()) {
                var scriptLine = result.getString(1);
                if (scriptLine == null || scriptLine.isBlank()) {
                    continue;
                }
                var trimmed = scriptLine.strip();
                if (!objectDefinitionStarted) {
                    objectDefinitionStarted = trimmed.regionMatches(true, 0, "CREATE ", 0, 7)
                            && trimmed.contains(qualifiedName);
                }
                if (objectDefinitionStarted && !trimmed.startsWith("--")) {
                    if (!definition.isEmpty()) {
                        definition.append(System.lineSeparator());
                    }
                    definition.append(trimmed);
                }
            }
        }

        return definition.isEmpty()
                ? Optional.empty()
                : Optional.of(definition.toString());
    }

    private static String quote(Connection connection, String identifier) throws SQLException {
        var quote = connection.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            return identifier;
        }
        return quote + identifier.replace(quote, quote + quote) + quote;
    }
}
