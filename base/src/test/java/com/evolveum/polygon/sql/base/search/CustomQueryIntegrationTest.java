/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.test.SqlIntegrationTestBase;
import com.evolveum.polygon.sql.base.test.SqlSchemaAssertions;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for fully custom SQL search via {@code custom { q -> ... }} Groovy DSL.
 */
@Test(singleThreaded = true)
public class CustomQueryIntegrationTest
        extends SqlIntegrationTestBase<CustomQueryIntegrationTest.TestConnector> {

    protected static class TestConnector extends DefaultTestConnector {
        protected TestConnector() { super(CUSTOM_QUERY_SCRIPT); }
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

    // ── Custom query – simple SELECT * with WHERE + ORDER BY ──

    @Test
    public void testCustomQueryWhereFilter() throws Exception {
        assertThat(search("accounts", null)).hasSize(2);
    }

    @Test
    public void testCustomQueryWhereFilterExcludesInactive() throws Exception {
        for (ConnectorObject obj : search("accounts", null)) {
            assertThat(getAttr(obj, "USERNAME")).isNotEqualTo("acct.closed");
        }
    }

    @Test
    public void testCustomQueryOrderByAsc() throws Exception {
        assertThat(search("accounts", null)).extracting(o -> getAttr(o, "USERNAME"))
                .containsExactly("acct.active", "acct.suspended");
    }

    @Test
    public void testCustomQueryConnIdFilterIgnored() throws Exception {
        // Custom queries don't automatically apply ConnId filters
        Filter emailFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("EMAIL", "acct.suspended@test.com"));
        assertThat(search("accounts", emailFilter)).hasSize(2);
    }

    // ── Custom query – WHERE with q.value() (filter passthrough) ──

    @Test
    public void testCustomQueryWithValueFilter() throws Exception {
        Filter userNameFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("USERNAME", "filter.user1"));
        List<ConnectorObject> results = search("custom_filter", userNameFilter);
        assertThat(results).hasSize(1);
        assertThat(getAttr(results.getFirst(), "USERNAME")).isEqualTo("filter.user1");
    }

    @Test
    public void testCustomQueryWithValueFilterNoMatch() throws Exception {
        Filter userNameFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("USERNAME", "nonexistent"));
        assertThat(search("custom_filter", userNameFilter)).isEmpty();
    }

    // ── Custom query – ne() predicate (NOT EQUALS) ──

    @Test
    public void testCustomQueryNePredicateExcludesDeleted() throws Exception {
        List<ConnectorObject> results = search("filter_ne", null);
        assertThat(results).hasSize(3);
        for (ConnectorObject obj : results) {
            assertThat(getAttr(obj, "STATUS")).isNotEqualTo("deleted");
        }
    }

    @Test
    public void testCustomQueryNeWithConnIdFilterIgnored() throws Exception {
        Filter statusFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("STATUS", "inactive"));
        assertThat(search("filter_ne", statusFilter)).hasSize(3);
    }

    // ── Combined clauses ──

    @Test
    public void testCustomQueryMultipleWherePredicates() throws Exception {
        List<ConnectorObject> results = search("multifilter", null);
        assertThat(results).hasSize(1);
        assertThat(getAttr(results.getFirst(), "USERNAME")).isEqualTo("mf.active");
    }

    // ── No custom query — built-in default ──

    @Test
    public void testNonCustomTableUsesBuiltIn() throws Exception {
        assertThat(search("builtin_table", null)).hasSize(2);
    }

    // ── Schema detection ──

    @Test
    public void testSchemaContainsCustomQueryTables() {
        SqlSchemaAssertions.sqlAssert(connector.schema())
                .hasObjectClassesInsensitive("accounts", "custom_filter", "filter_ne", "multifilter", "builtin_table");
    }

    // ── Groovy handler script ──

    private static final String CUSTOM_QUERY_SCRIPT = """
            objectClass("ACCOUNTS") {
                search {
                    sql {
                        custom { q ->
                            def t = q.table("accounts", "a")
                            q.select(t.column("ID"),
                                     t.column("USERNAME"), t.column("EMAIL"),
                                     t.column("STATUS"), t.column("ACTIVE"))
                             .from(t)
                             .where(t.column("ACTIVE").eq(true))
                             .orderBy(t.column("USERNAME").asc())
                        }
                    }
                }
            }
            objectClass("CUSTOM_FILTER") {
                search {
                    sql {
                        custom { q ->
                            def t = q.table("custom_filter", "c")
                            q.select(t.column("ID"),
                                     t.column("USERNAME"), t.column("EMAIL"),
                                     t.column("STATUS"))
                             .from(t)
                             .where(t.column("USERNAME").eq(q.value()))
                        }
                    }
                }
            }
            objectClass("FILTER_NE") {
                search {
                    sql {
                        custom { q ->
                            def t = q.table("filter_ne", "f")
                            q.select(t.column("ID"),
                                     t.column("USERNAME"), t.column("EMAIL"),
                                     t.column("STATUS"))
                             .from(t)
                             .where(t.column("STATUS").ne("deleted"))
                             .orderBy(t.column("USERNAME").asc())
                        }
                    }
                }
            }
            objectClass("MULTIFILTER") {
                search {
                    sql {
                        custom { q ->
                            def t = q.table("multifilter", "m")
                            q.select(t.column("ID"),
                                     t.column("USERNAME"),
                                     t.column("EMAIL"),
                                     t.column("STATUS"),
                                     t.column("ACTIVE"))
                             .from(t)
                             .where(t.column("STATUS").eq("active"))
                             .where(t.column("ACTIVE").eq(true))
                        }
                    }
                }
            }
            """;

    private static final String CREATE_SCHEMA_SQL = """
            DROP TABLE IF EXISTS filter_ne;
            DROP TABLE IF EXISTS multifilter;
            DROP TABLE IF EXISTS custom_filter;
            DROP TABLE IF EXISTS builtin_table;
            DROP TABLE IF EXISTS accounts;

            CREATE TABLE accounts (
                id INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email VARCHAR(255) UNIQUE,
                status VARCHAR(50) NOT NULL DEFAULT 'active',
                active BOOLEAN NOT NULL DEFAULT TRUE);

            CREATE TABLE custom_filter (
                id INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email VARCHAR(255) UNIQUE,
                status VARCHAR(50) NOT NULL DEFAULT 'active');

            CREATE TABLE filter_ne (
                id INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email VARCHAR(255) UNIQUE,
                status VARCHAR(50) NOT NULL DEFAULT 'active');

            CREATE TABLE multifilter (
                id INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email VARCHAR(255) UNIQUE,
                status VARCHAR(50) NOT NULL DEFAULT 'active',
                active BOOLEAN NOT NULL DEFAULT TRUE);

            CREATE TABLE builtin_table (
                id INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email VARCHAR(255) UNIQUE);
            """;

    private static final String INSERT_DATA_SQL = """
            INSERT INTO accounts (username, email, status, active) VALUES
                ('acct.active', 'acct.active@test.com', 'active', TRUE),
                ('acct.suspended', 'acct.suspended@test.com', 'suspended', TRUE),
                ('acct.closed', 'acct.closed@test.com', 'closed', FALSE);
            INSERT INTO custom_filter (username, email, status) VALUES
                ('filter.user1', 'filter.user1@test.com', 'active'),
                ('filter.user2', 'filter.user2@test.com', 'active');
            INSERT INTO filter_ne (username, email, status) VALUES
                ('filter.active', 'filter.active@test.com', 'active'),
                ('filter.suspended', 'filter.suspended@test.com', 'suspended'),
                ('filter.inactive', 'filter.inactive@test.com', 'inactive'),
                ('filter.deleted', 'filter.deleted@test.com', 'deleted');
            INSERT INTO multifilter (username, email, status, active) VALUES
                ('mf.active', 'mf.active@test.com', 'active', TRUE),
                ('mf.inactive', 'mf.inactive@test.com', 'inactive', TRUE),
                ('mf.closed', 'mf.closed@test.com', 'closed', FALSE);
            INSERT INTO builtin_table (username, email) VALUES
                ('builtin1', 'builtin1@test.com'),
                ('builtin2', 'builtin2@test.com');
            """;
}
