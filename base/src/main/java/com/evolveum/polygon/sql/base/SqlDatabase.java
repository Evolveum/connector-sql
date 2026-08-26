/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import java.util.List;

/** Supported database products identified from JDBC metadata. */
public enum SqlDatabase {

    H2("H2"),
    MARIADB("MariaDB"),
    ORACLE("Oracle"),
    POSTGRESQL("PostgreSQL"),
    UNKNOWN();

    private final List<String> jdbcProductNames;

    SqlDatabase(String... jdbcProductNames) {
        this.jdbcProductNames = List.of(jdbcProductNames);
    }

    public static SqlDatabase fromJdbcProductName(String productName) {
        if (productName == null) {
            return UNKNOWN;
        }
        for (var database : values()) {
            if (database.jdbcProductNames.stream()
                    .anyMatch(productName::equalsIgnoreCase)) {
                return database;
            }
        }
        return UNKNOWN;
    }
}
