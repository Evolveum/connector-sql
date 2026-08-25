/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.definition;

import java.util.List;
import java.util.Optional;

/** Registry of database-specific table definition providers. */
public final class SqlTableDefinitionProviders {

    private static final List<SqlTableDefinitionProvider> PROVIDERS = List.of(
            new H2SqlTableDefinitionProvider());

    private SqlTableDefinitionProviders() {
    }

    public static Optional<SqlTableDefinitionProvider> find(String databaseProductName) {
        return PROVIDERS.stream()
                .filter(provider -> provider.supports(databaseProductName))
                .findFirst();
    }
}
