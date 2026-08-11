/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.sql.RelationalPathBase;
import groovy.lang.Closure;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for custom SQL search features using Groovy DSL configuration.
 * Verifies WHERE clause filtering, table info lookup, and Groovy closure evaluation.
 */
@Test(singleThreaded = true)
public class CustomSearchIntegrationTest {

    private static final String URL = "jdbc:h2:mem:customsearch;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private TestConnector connector;

    /**
     * Connector that loads Groovy handler scripts configuring the WHERE clause.
     */
    private static class TestConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        TestConnector() { super(false); }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) {
            // Load Groovy script that configures search with WHERE clause
            builder.loadFromString(GROOVY_HANDLER_SCRIPT);
        }

        @Override
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) {
            // Auto-discover
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute(CREATE_SCHEMA_SQL);
            stmt.execute(INSERT_DATA_SQL);
            stmt.execute("COMMIT");
        }

        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(URL);
        config.setUsername("sa");
        config.setPassword(new GuardedString("".toCharArray()));
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setScanTables(true);
        config.setScanViews(true);
        config.setDevelopmentMode(true);

        connector = new TestConnector();
        connector.init(config);
    }

    @AfterMethod
    public void tearDown() {
        if (connector != null) {
            connector.dispose();
            connector = null;
        }
    }

    private OperationOptions opts() {
        return new OperationOptions(Collections.emptyMap());
    }

    private List<ConnectorObject> search(String table, Filter filter) throws Exception {
        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(new ObjectClass(table), filter, results::add, opts());
        return results;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Schema and table info
    // ──────────────────────────────────────────────────────────────────────

    @Test
    public void testSchemaContainsAllTables() {
        List<String> names = connector.schema().getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType)
                .map(String::toLowerCase)
                .toList();
        assertThat(names).contains("users", "accounts", "public_users");
    }

    @Test
    public void testTableInfosPopulated() {
        assertThat(connector.context().getTableInfos()).isNotNull();
        assertThat(connector.context().getTableInfos().keySet())
                .contains("users", "accounts", "public_users");
    }

    @Test
    public void testFindTableInfo() {
        var users = connector.context().findTableInfo("users");
        assertThat(users).isNotNull();
        assertThat(users.getColumns()).isNotEmpty();
        assertThat(users.getColumns().stream()
                .map(c -> c.getName().toLowerCase())
                .toList()).contains("id", "username", "email", "status", "legacy");
    }

    @Test
    public void testFindTableInfoCaseInsensitive() {
        assertThat(connector.context().findTableInfo("USERS")).isNotNull();
        assertThat(connector.context().findTableInfo("Users")).isNotNull();
        assertThat(connector.context().findTableInfo("nonexistent")).isNull();
    }

    @Test
    public void testFindTableInfoReturnsCorrectColumns() {
        var accounts = connector.context().findTableInfo("accounts");
        assertThat(accounts.getColumns()).isNotEmpty();
        assertThat(accounts.getColumns().stream()
                .map(c -> c.getName().toLowerCase())
                .toList()).contains("id", "username", "email", "status", "active");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Groovy WHERE clause – users table (excludes legacy rows)
    // ──────────────────────────────────────────────────────────────────────
    //
    // The Groovy script configures:
    //   builtIn { where { e -> e.legacy == false } }
    // So only non-legacy rows should ever return.
    //

    @Test
    public void testGroovyWhereExcludesLegacy() throws Exception {
        // Without the WHERE clause we'd see 4 rows (including alice.legacy).
        // With WHERE legacy == false we should see only 3.
        List<ConnectorObject> results = search("users", null);
        assertThat(results).hasSize(3);

        // Verify none are legacy
        for (ConnectorObject obj : results) {
            var attr = obj.getAttributeByName("LEGACY");
            if (attr != null && !attr.getValue().isEmpty()) {
                assertThat(attr.getValue().getFirst()).isEqualTo(false);
            }
        }
    }

    @Test
    public void testGroovyWhereWithConnIdFilter() throws Exception {
        // Combine the Groovy WHERE (legacy == false) with a ConnId filter
        Filter emailFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("EMAIL", "john@test.com"));
        List<ConnectorObject> results = search("users", emailFilter);
        assertThat(results).hasSize(1);
        assertThat(getAttr(results.getFirst(), "USERNAME")).isEqualTo("john.doe");
    }

    @Test
    public void testGroovyWhereFilteredSearch() throws Exception {
        // The WHERE clause (legacy == false) is always applied.
        // When we ask for the legacy user, we should get zero results.
        Filter userNameFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("USERNAME", "alice.legacy"));
        List<ConnectorObject> results = search("users", userNameFilter);
        assertThat(results).isEmpty();
    }

    @Test
    public void testGroovyWhereDoesNotAffectUnrelatedObjectClass() throws Exception {
        // 'accounts' has no WHERE clause configured, so all rows return
        assertThat(search("accounts", null)).hasSize(3);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Groovy WHERE clause – public_users view (excludes status='deleted')
    // ──────────────────────────────────────────────────────────────────────

    @Test
    public void testGroovyWhereOnViewExcludesDeleted() throws Exception {
        // public_users has where { e -> e.col("status").ne("deleted") }
        // 4 rows, 1 has status='deleted' (alice.legacy), so 3 should return
        List<ConnectorObject> results = search("public_users", null);
        assertThat(results).hasSize(3);
    }

    @Test
    public void testGroovyWhereOnViewWithFilter() throws Exception {
        // Combine Groovy WHERE (status.ne('deleted')) with ConnId filter
        Filter emailFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("EMAIL", "jane@test.com"));
        List<ConnectorObject> results = search("public_users", emailFilter);
        assertThat(results).hasSize(1);
        assertThat(getAttr(results.getFirst(), "USERNAME")).isEqualTo("jane.smith");
    }

    @Test
    public void testGroovyWhereExcludesDeletedWithFilter() throws Exception {
        // alice.legacy has status='deleted' — Groovy WHERE excludes her even
        // though ConnId filter matches by username
        Filter usernameFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("USERNAME", "alice.legacy"));
        List<ConnectorObject> results = search("public_users", usernameFilter);
        assertThat(results).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  SqlWherePredicateBuilder unit tests (Java API without Groovy)
    // ──────────────────────────────────────────────────────────────────────

    @Test
    public void testPredicateBuilderColAccess() {
        var context = connector.context();
        RelationalPathBase<?> tablePath = newTablePath("users", "u");
        var builder = new SqlWherePredicateBuilder(tablePath, context);
        assertThat(builder.col("username")).isNotNull();
    }

    @Test
    public void testPredicateBuilderEq() {
        var context = connector.context();
        var builder = new SqlWherePredicateBuilder(newTablePath("users", "u"), context);
        builder.col("status").eq("active");
        assertThat(builder.build()).isNotNull();
        assertThat(builder.build().toString()).contains("status");
    }

    @Test
    public void testPredicateBuilderNe() {
        var context = connector.context();
        var builder = new SqlWherePredicateBuilder(newTablePath("users", "u"), context);
        builder.col("status").ne("deleted");
        var pred = builder.build();
        assertThat(pred).isNotNull();
        assertThat(pred.toString()).contains("status");
    }

    @Test
    public void testPredicateBuilderMultipleConditions() {
        var context = connector.context();
        var builder = new SqlWherePredicateBuilder(newTablePath("users", "u"), context);
        builder.col("status").eq("active");
        builder.col("legacy").eq(false);
        var pred = builder.build();
        assertThat(pred).isNotNull();
        assertThat(pred.toString()).contains("status").contains("legacy");
    }

    @Test
    public void testPredicateBuilderEmpty() {
        var context = connector.context();
        var builder = new SqlWherePredicateBuilder(newTablePath("users", "u"), context);
        assertThat(builder.build()).isNull();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  SqlSearchOperation runtime evaluation
    // ──────────────────────────────────────────────────────────────────────

    @Test
    public void testSearchOperationWithNoWhereClause() throws Exception {
        var context = connector.context();
        var ocDef = context.schema().objectClass(new ObjectClass("accounts"));
        assertThat(ocDef).isNotNull();

        var op = new SqlSearchOperation(context, ocDef, (Closure<?>) null);
        List<ConnectorObject> results = new ArrayList<>();
        op.executeQuery(context, null, results::add, opts());
        assertThat(results).hasSize(3);  // all accounts
    }

    // ──────────────────────────────────────────────────────────────────────

    private RelationalPathBase<?> newTablePath(String tableName, String alias) {
        return new RelationalPathBase<>(Object.class,
                PathMetadataFactory.forVariable(alias),
                null, tableName);
    }

    private Object getAttr(ConnectorObject o, String name) {
        var attr = o.getAttributeByName(name);
        return (attr != null && !attr.getValue().isEmpty()) ? attr.getValue().getFirst() : null;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Custom query examples (documenting usage — not currently exercised at runtime)
    // ──────────────────────────────────────────────────────────────────────
    //
    // Custom search allows full control over SELECT, FROM, WHERE, ORDER BY:
    //
    // objectClass("ACCOUNTS") {
    //     search {
    //         sql {
    //             custom { q ->
    //                 def t = q.table("accounts", "a")
    //                 q.select(t.column("ID"),
    //                          t.column("USERNAME"),
    //                          t.column("EMAIL"))
    //                  .from(t)
    //                  .where(t.column("ACTIVE").eq(true))
    //                  .orderBy(t.column("USERNAME").asc())
    //             }
    //         }
    //     }
    // }
    //
    // Filter value access (single attribute filter → q.value()):
    //
    // custom { q ->
    //     def t = q.table("accounts", "a")
    //     q.select(t.column("ID"), t.column("USERNAME"))
    //      .from(t)
    //      .where(t.column("USERNAME").eq(q.value()))
    // }
    //
    // Custom query disables built-in handler automatically to avoid
    // multiple emptyFilter handlers conflict.
    //

    // ──────────────────────────────────────────────────────────────────────
    //  Groovy handler script loaded at connector init time
    // ──────────────────────────────────────────────────────────────────────
    //
    // Configures:
    //   - USERS:        where { e -> e.col("LEGACY").eq(false) }    → exclude legacy rows
    //   - PUBLIC_USERS: where { e -> e.col("STATUS").ne("deleted") } → exclude deleted rows
    //   - ACCOUNTS:     (no WHERE clause – all rows)                → no filtering
    //
    // Note: Table names must match schema naming convention (uppercase).
    //       Use explicit .eq() and .ne() for correct QueryDSL semantics —
    //       Groovy's == and != operators map to Java equals() and !equals().
    //

    private static final String GROOVY_HANDLER_SCRIPT = """
            objectClass("USERS") {
                search {
                    sql {
                        builtIn {
                            enabled true
                            where { e ->
                                e.col("LEGACY").eq(false)
                            }
                        }
                    }
                }
            }

            objectClass("PUBLIC_USERS") {
                search {
                    sql {
                        builtIn {
                            enabled true
                            where { e ->
                                e.col("STATUS").ne("deleted")
                            }
                        }
                    }
                }
            }
            """;

    // ──────────────────────────────────────────────────────────────────────
    //  Test DDL + data
    // ──────────────────────────────────────────────────────────────────────

    private static final String CREATE_SCHEMA_SQL = """
            DROP TABLE IF EXISTS accounts;
            DROP TABLE IF EXISTS public_users;
            DROP TABLE IF EXISTS users;

            CREATE TABLE users (
                id       INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email    VARCHAR(255) UNIQUE,
                status   VARCHAR(50)  NOT NULL DEFAULT 'active',
                legacy   BOOLEAN      NOT NULL DEFAULT FALSE,
                created_at TIMESTAMP
            );

            -- public_users: same structure as users, will be filtered by WHERE clause
            CREATE TABLE public_users (
                id       INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email    VARCHAR(255) UNIQUE,
                status   VARCHAR(50)  NOT NULL DEFAULT 'active',
                legacy   BOOLEAN      NOT NULL DEFAULT FALSE,
                created_at TIMESTAMP
            );

            CREATE TABLE accounts (
                id       INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email    VARCHAR(255) UNIQUE,
                status   VARCHAR(50)  NOT NULL DEFAULT 'active',
                active   BOOLEAN      NOT NULL DEFAULT TRUE
            );
            """;

    private static final String INSERT_DATA_SQL = """
            INSERT INTO users (username, email, status, legacy) VALUES
                ('john.doe',     'john@test.com',    'active',    FALSE),
                ('jane.smith',   'jane@test.com',    'active',    FALSE),
                ('bob.wilson',   'bob@test.com',     'inactive',  FALSE),
                ('alice.legacy', 'alice@test.com',   'deleted',   TRUE);

            -- public_users: same rows, but with different status for alice
            INSERT INTO public_users (username, email, status, legacy) VALUES
                ('john.doe',     'john@test.com',    'active',    FALSE),
                ('jane.smith',   'jane@test.com',    'active',    FALSE),
                ('bob.wilson',   'bob@test.com',     'inactive',  FALSE),
                ('alice.legacy', 'alice@test.com',   'deleted',   TRUE);

            INSERT INTO accounts (username, email, status, active) VALUES
                ('acct.active',    'acct.active@test.com',    'active',    TRUE),
                ('acct.suspended', 'acct.suspended@test.com', 'suspended', TRUE),
                ('acct.closed',    'acct.closed@test.com',    'closed',    FALSE);
            """;
}
