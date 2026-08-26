/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlDatabaseTest {

    @DataProvider
    public static Object[][] jdbcProductNames() {
        return new Object[][] {
                { "H2", SqlDatabase.H2 },
                { "h2", SqlDatabase.H2 },
                { "MariaDB", SqlDatabase.MARIADB },
                { "mariadb", SqlDatabase.MARIADB },
                { "Oracle", SqlDatabase.ORACLE },
                { "oracle", SqlDatabase.ORACLE },
                { "PostgreSQL", SqlDatabase.POSTGRESQL },
                { "postgresql", SqlDatabase.POSTGRESQL },
                { "Unsupported database", SqlDatabase.UNKNOWN },
                { null, SqlDatabase.UNKNOWN }
        };
    }

    @Test(dataProvider = "jdbcProductNames")
    public void mapsJdbcProductName(String productName, SqlDatabase expected) {
        assertThat(SqlDatabase.fromJdbcProductName(productName)).isEqualTo(expected);
    }
}
