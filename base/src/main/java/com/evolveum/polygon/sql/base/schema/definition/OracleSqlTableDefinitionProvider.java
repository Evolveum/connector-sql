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

/** Reads native Oracle table and view definitions with {@code DBMS_METADATA.GET_DDL}. */
public final class OracleSqlTableDefinitionProvider implements SqlTableDefinitionProvider {

    private static final String GET_DDL = "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) FROM DUAL";

    @Override
    public SqlDatabase database() {
        return SqlDatabase.ORACLE;
    }

    @Override
    public Optional<String> readDefinition(Connection connection, String catalog, String schema,
            String name, String tableType) throws SQLException {
        var objectType = "VIEW".equalsIgnoreCase(tableType) ? "VIEW" : "TABLE";
        var owner = schema == null || schema.isBlank()
                ? connection.getMetaData().getUserName()
                : schema;

        try (var statement = connection.prepareStatement(GET_DDL)) {
            statement.setString(1, objectType);
            statement.setString(2, name);
            statement.setString(3, owner);
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
