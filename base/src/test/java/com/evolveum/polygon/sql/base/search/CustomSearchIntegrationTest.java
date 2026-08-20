/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.test.SqlIntegrationTestBase;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.sql.RelationalPathBase;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for custom SQL search features using Groovy DSL configuration.
 * Verifies WHERE clause filtering, table info lookup, and Groovy closure evaluation.
 */
@Test(singleThreaded = true)
public class CustomSearchIntegrationTest
        extends SqlIntegrationTestBase<CustomSearchIntegrationTest.TestConnector> {

    protected static class TestConnector extends DefaultTestConnector {
        protected TestConnector() { super(GROOVY_HANDLER_SCRIPT); }
    }

    @Override
    protected String schemaSql() {
        return CREATE_SCHEMA_SQL;
    }

    @Override
    protected String dataSql() {
        return INSERT_DATA_SQL;
    }

    @Override
    protected void initConnector() {
        connector = new TestConnector();
        connector.init(defaultConfig());
    }

    // ── Schema and table info ──

    @Test
    public void testSchemaContainsAllTables() {
        initConnector();
        assertThat(schemaNames()).contains("users", "accounts", "public_users");
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

    // ── Groovy WHERE clause – users table (excludes legacy rows) ──
    // The Groovy script configures: builtIn { where { e -> e.legacy == false } }

    @Test
    public void testGroovyWhereExcludesLegacy() throws Exception {
        List<ConnectorObject> results = search("users", null);
        assertThat(results).hasSize(3);
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
        assertThat(search("users", userNameFilter)).isEmpty();
    }

    @Test
    public void testGroovyWhereDoesNotAffectUnrelatedObjectClass() throws Exception {
        // 'accounts' has no WHERE clause configured, so all rows return
        assertThat(search("accounts", null)).hasSize(3);
    }

    // ── Groovy WHERE clause – public_users view (excludes status='deleted') ──

    @Test
    public void testGroovyWhereOnViewExcludesDeleted() throws Exception {
        assertThat(search("public_users", null)).hasSize(3);
    }

    @Test
    public void testGroovyWhereOnViewWithFilter() throws Exception {
        Filter emailFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("EMAIL", "jane@test.com"));
        List<ConnectorObject> results = search("public_users", emailFilter);
        assertThat(results).hasSize(1);
        assertThat(getAttr(results.getFirst(), "USERNAME")).isEqualTo("jane.smith");
    }

    @Test
    public void testGroovyWhereExcludesDeletedWithFilter() throws Exception {
        Filter usernameFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("USERNAME", "alice.legacy"));
        assertThat(search("public_users", usernameFilter)).isEmpty();
    }

    // ── SqlWherePredicateBuilder unit tests (Java API without Groovy) ──

    private RelationalPathBase<?> newTablePath(String tableName, String alias) {
        return new RelationalPathBase<>(Object.class,
                PathMetadataFactory.forVariable(alias), null, tableName);
    }

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
        var pred = builder.build();
        assertThat(pred).isNotNull();
        assertThat(pred.toString()).contains("status");
    }

    @Test
    public void testPredicateBuilderNe() {
        var context = connector.context();
        var builder = new SqlWherePredicateBuilder(newTablePath("users", "u"), context);
        builder.col("status").ne("deleted");
        assertThat(builder.build().toString()).contains("status");
    }

    @Test
    public void testPredicateBuilderMultipleConditions() {
        var context = connector.context();
        var builder = new SqlWherePredicateBuilder(newTablePath("users", "u"), context);
        builder.col("status").eq("active");
        builder.col("legacy").eq(false);
        var pred = builder.build();
        assertThat(pred.toString()).contains("status").contains("legacy");
    }

    @Test
    public void testPredicateBuilderEmpty() {
        var context = connector.context();
        var builder = new SqlWherePredicateBuilder(newTablePath("users", "u"), context);
        assertThat(builder.build()).isNull();
    }

    // ── SqlSearchOperation runtime evaluation ──

    @Test
    public void testSearchOperationWithNoWhereClause() throws Exception {
        var context = connector.context();
        var ocDef = context.schema().objectClass(new ObjectClass("accounts"));
        assertThat(ocDef).isNotNull();
        var op = new SqlSearchOperation(context, ocDef, (Closure<?>) null);
        List<ConnectorObject> results = new ArrayList<>();
        op.executeQuery(context, null, results::add, opts());
        assertThat(results).hasSize(3);
    }

    // ── Groovy handler script ──
    // USERS:        where { e -> e.col("LEGACY").eq(false) }
    // PUBLIC_USERS: where { e -> e.col("STATUS").ne("deleted") }
    // ACCOUNTS:     (no WHERE clause)

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

    // ── Test DDL + data ──

    private static final String CREATE_SCHEMA_SQL = """
            DROP TABLE IF EXISTS accounts CASCADE;
            DROP TABLE IF EXISTS public_users CASCADE;
            DROP TABLE IF EXISTS users CASCADE;

            CREATE TABLE users (
                id       INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email    VARCHAR(255) UNIQUE,
                status   VARCHAR(50)  NOT NULL DEFAULT 'active',
                legacy   BOOLEAN      NOT NULL DEFAULT FALSE,
                created_at TIMESTAMP
            );

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
