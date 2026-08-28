/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.definition;

import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.SqlDatabase;

import java.util.List;
import java.util.Optional;

/** Registry of database-specific table definition providers. */
public final class SqlTableDefinitionProviders {

    private SqlTableDefinitionProviders() {
    }

    public static Optional<SqlTableDefinitionProvider> find(
            SqlDatabase database, SqlConnectorConfiguration configuration) {
        return List.of(
                        new H2SqlTableDefinitionProvider(),
                        new MySqlFamilyTableDefinitionProvider(SqlDatabase.MARIADB),
                        new MySqlFamilyTableDefinitionProvider(SqlDatabase.MYSQL),
                        new OracleSqlTableDefinitionProvider(),
                        new PostgreSqlTableDefinitionProvider(configuration),
                        new SqliteTableDefinitionProvider())
                .stream()
                .filter(provider -> provider.database() == database)
                .findFirst();
    }
}
