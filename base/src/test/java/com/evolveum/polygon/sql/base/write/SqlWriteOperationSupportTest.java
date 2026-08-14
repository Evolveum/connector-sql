/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlWriteOperationSupportTest {

    @Test
    public void testVendorDuplicateCodes() {
        assertThat(SqlWriteOperationSupport.isDuplicate(
                new SQLException("duplicate", "23505", 0))).isTrue();
        assertThat(SqlWriteOperationSupport.isDuplicate(
                new SQLException("ORA-00001", "23000", 1))).isTrue();
        assertThat(SqlWriteOperationSupport.isDuplicate(
                new SQLException("SQLite generic error", null, 1))).isFalse();
    }

    @Test
    public void testSqliteDistinguishesDuplicateAndOtherErrors() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE records (
                        id INTEGER PRIMARY KEY,
                        external_id TEXT NOT NULL UNIQUE,
                        required_value TEXT NOT NULL
                    )
                    """);
            statement.execute("INSERT INTO records VALUES (1, 'one', 'value')");

            assertThat(SqlWriteOperationSupport.isDuplicate(failure(statement,
                    "INSERT INTO records VALUES (2, 'one', 'value')"))).isTrue();
            assertThat(SqlWriteOperationSupport.isDuplicate(failure(statement,
                    "INSERT INTO records VALUES (1, 'two', 'value')"))).isTrue();
            assertThat(SqlWriteOperationSupport.isDuplicate(failure(statement,
                    "INSERT INTO records VALUES (3, 'three', NULL)"))).isFalse();
            assertThat(SqlWriteOperationSupport.isDuplicate(failure(statement,
                    "INSERT INTO missing_table VALUES (1)"))).isFalse();
        }
    }

    private SQLException failure(Statement statement, String sql) {
        try {
            statement.execute(sql);
            throw new AssertionError("Expected SQL statement to fail: " + sql);
        } catch (SQLException e) {
            return e;
        }
    }
}
