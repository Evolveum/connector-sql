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

/** Database-specific reader of a table or view SQL definition. */
public interface SqlTableDefinitionProvider {

    SqlDatabase database();

    Optional<String> readDefinition(Connection connection, String catalog, String schema,
            String name, String tableType) throws SQLException;
}
