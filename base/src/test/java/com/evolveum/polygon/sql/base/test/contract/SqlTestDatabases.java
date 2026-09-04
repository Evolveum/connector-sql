/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.test.contract;

import com.evolveum.polygon.sql.base.SqlDatabase;
import com.evolveum.polygon.sql.base.test.PostgresDatabaseInitializer;
import com.evolveum.polygon.sql.base.test.SqliteDatabaseInitializer;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Factory for the six database configurations supported by the contract suite. */
public final class SqlTestDatabases {

    private static final AutoCloseable NOOP_CLOSE = () -> { };

    private SqlTestDatabases() {
    }

    public static SqlTestDatabase h2() {
        var id = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
        return database(
                SqlDatabase.H2, false, true, true, true, true, true,
                "jdbc:h2:mem:contract_" + id + ";DB_CLOSE_DELAY=-1",
                "sa", "", "database/h2/contract-schema.sql",
                List.of(), standardDrops(" CASCADE"), List.of(), NOOP_CLOSE);
    }

    public static SqlTestDatabase postgresql() {
        var postgres = PostgresDatabaseInitializer.create();
        return database(
                SqlDatabase.POSTGRESQL, false, true, true,
                !System.getProperty("sql.test.postgresql.pgDumpPath", "").isBlank(),
                true, true,
                postgres.getJdbcUrl(), postgres.getUsername(), "postgres",
                "database/postgresql/contract-schema.sql",
                List.of(), standardDrops(" CASCADE"), List.of(), postgres);
    }

    public static SqlTestDatabase sqlite() throws Exception {
        var sqlite = SqliteDatabaseInitializer.create();
        return database(
                SqlDatabase.SQLITE, false, false, false, true, true, true,
                sqlite.jdbcUrl() + "?foreign_keys=on", "unused", "",
                "database/sqlite/contract-schema.sql",
                List.of("PRAGMA foreign_keys = OFF"), standardDrops(""),
                List.of("PRAGMA foreign_keys = ON"), sqlite);
    }

    public static SqlTestDatabase oracle() {
        return database(
                SqlDatabase.ORACLE, true, true, false, true, false, false,
                setting("sql.test.oracle.url", "SQL_TEST_ORACLE_URL",
                        "jdbc:oracle:thin:@//localhost:1521/FREEPDB1"),
                setting("sql.test.oracle.username", "SQL_TEST_ORACLE_USERNAME", "oracle"),
                setting("sql.test.oracle.password", "SQL_TEST_ORACLE_PASSWORD", "oracle123"),
                "database/oracle/contract-schema.sql",
                List.of(), oracleDrops(), List.of(), NOOP_CLOSE);
    }

    public static SqlTestDatabase mariadb() {
        return database(
                SqlDatabase.MARIADB, true, false, true, true, true, true,
                setting("sql.test.mariadb.url", "SQL_TEST_MARIADB_URL",
                        "jdbc:mariadb://localhost:3307/connector_sql"),
                setting("sql.test.mariadb.username", "SQL_TEST_MARIADB_USERNAME", "connector"),
                setting("sql.test.mariadb.password", "SQL_TEST_MARIADB_PASSWORD", "connector123"),
                "database/mariadb/contract-schema.sql",
                List.of(), standardDrops(""), List.of(), NOOP_CLOSE);
    }

    public static SqlTestDatabase mysql() {
        return database(
                SqlDatabase.MYSQL, true, false, true, true, true, true,
                setting("sql.test.mysql.url", "SQL_TEST_MYSQL_URL",
                        "jdbc:mysql://localhost:3308/connector_sql?allowPublicKeyRetrieval=true&useSSL=false"),
                setting("sql.test.mysql.username", "SQL_TEST_MYSQL_USERNAME", "connector"),
                setting("sql.test.mysql.password", "SQL_TEST_MYSQL_PASSWORD", "connector123"),
                "database/mysql/contract-schema.sql",
                List.of(), standardDrops(""), List.of(), NOOP_CLOSE);
    }

    private static JdbcSqlTestDatabase database(
            SqlDatabase database,
            boolean external,
            boolean supportsSchemas,
            boolean supportsRemarks,
            boolean supportsNativeDefinitions,
            boolean supportsJdbcDefaults,
            boolean supportsNonPrimaryForeignKeyMetadata,
            String jdbcUrl,
            String username,
            String password,
            String resource,
            List<String> beforeDrop,
            List<String> drops,
            List<String> afterDrop,
            AutoCloseable closeAction) {
        return new JdbcSqlTestDatabase(
                database,
                new DatabaseCapabilities(
                        external, supportsSchemas, supportsRemarks,
                        supportsNativeDefinitions, supportsJdbcDefaults,
                        supportsNonPrimaryForeignKeyMetadata),
                jdbcUrl, username, password, resource,
                beforeDrop, drops, afterDrop, closeAction);
    }

    private static List<String> standardDrops(String tableSuffix) {
        return List.of(
                "DROP VIEW IF EXISTS contract_user_view",
                "DROP TABLE IF EXISTS contract_user_phone" + tableSuffix,
                "DROP TABLE IF EXISTS contract_user_email" + tableSuffix,
                "DROP TABLE IF EXISTS contract_user_profile" + tableSuffix,
                "DROP TABLE IF EXISTS contract_user_alias" + tableSuffix,
                "DROP TABLE IF EXISTS contract_address" + tableSuffix,
                "DROP TABLE IF EXISTS contract_user_group" + tableSuffix,
                "DROP TABLE IF EXISTS contract_composite_tag" + tableSuffix,
                "DROP TABLE IF EXISTS contract_composite" + tableSuffix,
                "DROP TABLE IF EXISTS contract_external" + tableSuffix,
                "DROP TABLE IF EXISTS contract_group" + tableSuffix,
                "DROP TABLE IF EXISTS contract_user" + tableSuffix);
    }

    private static List<String> oracleDrops() {
        return List.of(
                "DROP VIEW contract_user_view",
                "DROP TABLE contract_user_phone CASCADE CONSTRAINTS PURGE",
                "DROP TABLE contract_user_email CASCADE CONSTRAINTS PURGE",
                "DROP TABLE contract_user_profile CASCADE CONSTRAINTS PURGE",
                "DROP TABLE contract_user_alias CASCADE CONSTRAINTS PURGE",
                "DROP TABLE contract_address CASCADE CONSTRAINTS PURGE",
                "DROP TABLE contract_user_group CASCADE CONSTRAINTS PURGE",
                "DROP TABLE contract_composite_tag CASCADE CONSTRAINTS PURGE",
                "DROP TABLE contract_composite CASCADE CONSTRAINTS PURGE",
                "DROP TABLE contract_external CASCADE CONSTRAINTS PURGE",
                "DROP TABLE contract_group CASCADE CONSTRAINTS PURGE",
                "DROP TABLE contract_user CASCADE CONSTRAINTS PURGE");
    }

    private static String setting(String property, String environment, String defaultValue) {
        var configured = System.getProperty(property);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        configured = System.getenv(environment);
        return configured != null && !configured.isBlank() ? configured : defaultValue;
    }
}
