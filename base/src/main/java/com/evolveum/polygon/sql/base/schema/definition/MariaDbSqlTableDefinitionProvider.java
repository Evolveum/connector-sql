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

/** Reads native MariaDB table and view definitions with {@code SHOW CREATE}. */
public final class MariaDbSqlTableDefinitionProvider implements SqlTableDefinitionProvider {

    @Override
    public SqlDatabase database() {
        return SqlDatabase.MARIADB;
    }

    @Override
    public Optional<String> readDefinition(Connection connection, String catalog, String schema,
            String name, String tableType) throws SQLException {
        var objectType = "VIEW".equalsIgnoreCase(tableType) ? "VIEW" : "TABLE";
        var namespace = schema == null || schema.isBlank() ? catalog : schema;
        var qualifiedName = namespace == null || namespace.isBlank()
                ? quote(connection, name)
                : quote(connection, namespace) + "." + quote(connection, name);

        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SHOW CREATE " + objectType + " " + qualifiedName)) {
            if (!result.next()) {
                return Optional.empty();
            }
            var definition = result.getString(2);
            return definition == null || definition.isBlank()
                    ? Optional.empty()
                    : Optional.of(definition.strip());
        }
    }

    private static String quote(Connection connection, String identifier) throws SQLException {
        var quote = connection.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.isBlank()) {
            return identifier;
        }
        return quote + identifier.replace(quote, quote + quote) + quote;
    }
}
