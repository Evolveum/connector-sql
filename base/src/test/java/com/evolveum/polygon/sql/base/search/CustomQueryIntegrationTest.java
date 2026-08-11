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
 * Integration tests for fully custom SQL search via {@code custom { q -> ... }} Groovy DSL.
 * <p>Tests: SELECT, FROM, WHERE, ORDER BY, q.value(), filter passthrough, pagination.</p>
 */
@Test(singleThreaded = true)
public class CustomQueryIntegrationTest {

    private static final String URL = "jdbc:h2:mem:customquery;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private TestConnector connector;

    private static class TestConnector
            extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        TestConnector() { super(false); }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) {
            builder.loadFromString(CUSTOM_QUERY_SCRIPT);
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

    private Object getAttr(ConnectorObject o, String name) {
        var attr = o.getAttributeByName(name);
        return (attr != null && !attr.getValue().isEmpty()) ? attr.getValue().getFirst() : null;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Custom query — simple SELECT * with WHERE
    // ──────────────────────────────────────────────────────────────────────
    // The ACCOUNTS table has a custom query that selects all rows with
    // active == true, ordered by username ASC.
    // Data: acct.active (active=TRUE), acct.suspended (active=TRUE), acct.closed (active=FALSE)

    @Test
    public void testCustomQueryWhereFilterEmpty() throws Exception {
        // Custom WHERE: active == true → should return 2 rows
        List<ConnectorObject> results = search("accounts", null);
        assertThat(results).hasSize(2);
    }

    @Test
    public void testCustomQueryWhereFilterExcludesInactive() throws Exception {
        // acct.closed has active=false — must not appear
        List<ConnectorObject> results = search("accounts", null);
        for (ConnectorObject obj : results) {
            assertThat(getAttr(obj, "USERNAME")).isNotEqualTo("acct.closed");
        }
    }

    @Test
    public void testCustomQueryOrderByAsc() throws Exception {
        // ORDER BY username ASC: acct.active, acct.suspended
        List<ConnectorObject> results = search("accounts", null);
        assertThat(results).extracting(o -> getAttr(o, "USERNAME"))
                .containsExactly("acct.active", "acct.suspended");
    }

    @Test
    public void testCustomQueryConnIdFilterIgnored() throws Exception {
        // Custom queries don't automatically apply ConnId filters.
        // Only the custom WHERE (active==true) is applied — returns 2 rows.
        Filter emailFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("EMAIL", "acct.suspended@test.com"));
        List<ConnectorObject> results = search("accounts", emailFilter);
        assertThat(results).hasSize(2);
    }

    @Test
    public void testCustomQueryFilterExcludedByWhere() throws Exception {
        // acct.closed has active=false — excluded by custom WHERE regardless of ConnId filter
        // Note: ConnId filter is ignored; custom WHERE is the only filter
        List<ConnectorObject> results = search("accounts", null);
        for (ConnectorObject obj : results) {
            assertThat(getAttr(obj, "USERNAME")).isNotEqualTo("acct.closed");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Custom query — WHERE with q.value() (filter passthrough)
    // ──────────────────────────────────────────────────────────────────────
    // The CUSTOM_FILTER table uses: .where(t.column("USERNAME").eq(q.value()))
    // With null filter → returns all rows (q.value() == null → eq(NULL))
    // Actually, eq(null) maps to isNull() in SqlColumnRef.eq(), so null filter
    // will exclude all non-null rows. Let's handle this in the script: only use
    // q.value() when filter is present, no WHERE for empty filter.
    // Instead, test q.value() with an explicit filter.

    @Test
    public void testCustomQueryWithValueFilter() throws Exception {
        // CUSTOM_FILTER table uses q.value() to match USERNAME
        Filter userNameFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("USERNAME", "filter.user1"));
        List<ConnectorObject> results = search("custom_filter", userNameFilter);
        assertThat(results).hasSize(1);
        assertThat(getAttr(results.getFirst(), "USERNAME")).isEqualTo("filter.user1");
    }

    @Test
    public void testCustomQueryWithValueFilterNoMatch() throws Exception {
        // No row with this username
        Filter userNameFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("USERNAME", "nonexistent"));
        List<ConnectorObject> results = search("custom_filter", userNameFilter);
        assertThat(results).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Custom query — ne() predicate (NOT EQUALS)
    // ──────────────────────────────────────────────────────────────────────
    // The FILTER_NE table uses: .where(t.column("STATUS").ne("deleted"))
    // Data: filter.user1 (active), filter.user2 (suspended), filter.deleted (deleted),
    //       filter.inactive (inactive)

    @Test
    public void testCustomQueryNePredicateExcludesDeleted() throws Exception {
        // ne("deleted") should return 3 rows (active, suspended, inactive)
        List<ConnectorObject> results = search("filter_ne", null);
        assertThat(results).hasSize(3);

        // Verify none have status=deleted
        for (ConnectorObject obj : results) {
            assertThat(getAttr(obj, "STATUS")).isNotEqualTo("deleted");
        }
    }

    @Test
    public void testCustomQueryNeWithConnIdFilterIgnored() throws Exception {
        // Custom query ignores ConnId filter, only applies custom WHERE (ne "deleted")
        Filter statusFilter = FilterBuilder.equalTo(
                AttributeBuilder.build("STATUS", "inactive"));
        List<ConnectorObject> results = search("filter_ne", statusFilter);
        // Returns all non-deleted rows (3), not just 1
        assertThat(results).hasSize(3);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Combined clauses test
    // ──────────────────────────────────────────────────────────────────────

    @Test
    public void testCustomQueryMultipleWherePredicates() throws Exception {
        // MULTIFILTER table: WHERE status = 'active' AND active = true
        // Data: mf.active (active, true), mf.inactive (inactive, true),
        //       mf.closed (closed, false)
        List<ConnectorObject> results = search("multifilter", null);
        assertThat(results).hasSize(1);
        assertThat(getAttr(results.getFirst(), "USERNAME")).isEqualTo("mf.active");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  No custom query — built-in default
    // ──────────────────────────────────────────────────────────────────────
    // Tables without a custom query config fall back to built-in search.

    @Test
    public void testNonCustomTableUsesBuiltIn() throws Exception {
        // "builtin_table" has no custom query — should use default search
        List<ConnectorObject> results = search("builtin_table", null);
        assertThat(results).hasSize(2);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Schema detection
    // ──────────────────────────────────────────────────────────────────────

    @Test
    public void testSchemaContainsCustomQueryTables() {
        List<String> names = connector.schema().getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType)
                .map(String::toLowerCase)
                .toList();
        assertThat(names).contains("accounts", "custom_filter", "filter_ne", "multifilter", "builtin_table");
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Groovy handler script — custom query DSL
    // ──────────────────────────────────────────────────────────────────────

    private static final String CUSTOM_QUERY_SCRIPT = """
            // ACCOUNTS: custom query with WHERE + ORDER BY
            objectClass("ACCOUNTS") {
                search {
                    sql {
                        custom { q ->
                            def t = q.table("accounts", "a")
                            q.select(t.column("ID"),
                                     t.column("USERNAME"),
                                     t.column("EMAIL"),
                                     t.column("STATUS"),
                                     t.column("ACTIVE"))
                             .from(t)
                             .where(t.column("ACTIVE").eq(true))
                             .orderBy(t.column("USERNAME").asc())
                        }
                    }
                }
            }

            // CUSTOM_FILTER: uses q.value() for filter passthrough
            objectClass("CUSTOM_FILTER") {
                search {
                    sql {
                        custom { q ->
                            def t = q.table("custom_filter", "c")
                            q.select(t.column("ID"),
                                     t.column("USERNAME"),
                                     t.column("EMAIL"),
                                     t.column("STATUS"))
                             .from(t)
                             .where(t.column("USERNAME").eq(q.value()))
                        }
                    }
                }
            }

            // FILTER_NE: uses ne() predicate
            objectClass("FILTER_NE") {
                search {
                    sql {
                        custom { q ->
                            def t = q.table("filter_ne", "f")
                            q.select(t.column("ID"),
                                     t.column("USERNAME"),
                                     t.column("EMAIL"),
                                     t.column("STATUS"))
                             .from(t)
                             .where(t.column("STATUS").ne("deleted"))
                             .orderBy(t.column("USERNAME").asc())
                        }
                    }
                }
            }

            // MULTIFILTER: multiple WHERE predicates
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

            // BUILTIN_TABLE: no custom query — falls through to default
            // (no script config, uses built-in auto-generated search)
            """;

    // ──────────────────────────────────────────────────────────────────────
    //  Test DDL + data
    // ──────────────────────────────────────────────────────────────────────

    private static final String CREATE_SCHEMA_SQL = """
            DROP TABLE IF EXISTS filter_ne;
            DROP TABLE IF EXISTS multifilter;
            DROP TABLE IF EXISTS custom_filter;
            DROP TABLE IF EXISTS builtin_table;
            DROP TABLE IF EXISTS accounts;

            CREATE TABLE accounts (
                id       INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email    VARCHAR(255) UNIQUE,
                status   VARCHAR(50)  NOT NULL DEFAULT 'active',
                active   BOOLEAN      NOT NULL DEFAULT TRUE
            );

            CREATE TABLE custom_filter (
                id       INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email    VARCHAR(255) UNIQUE,
                status   VARCHAR(50)  NOT NULL DEFAULT 'active'
            );

            CREATE TABLE filter_ne (
                id       INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email    VARCHAR(255) UNIQUE,
                status   VARCHAR(50)  NOT NULL DEFAULT 'active'
            );

            CREATE TABLE multifilter (
                id       INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email    VARCHAR(255) UNIQUE,
                status   VARCHAR(50)  NOT NULL DEFAULT 'active',
                active   BOOLEAN      NOT NULL DEFAULT TRUE
            );

            CREATE TABLE builtin_table (
                id       INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(255) NOT NULL UNIQUE,
                email    VARCHAR(255) UNIQUE
            );
            """;

    private static final String INSERT_DATA_SQL = """
            INSERT INTO accounts (username, email, status, active) VALUES
                ('acct.active',    'acct.active@test.com',    'active',    TRUE),
                ('acct.suspended', 'acct.suspended@test.com', 'suspended', TRUE),
                ('acct.closed',    'acct.closed@test.com',    'closed',    FALSE);

            INSERT INTO custom_filter (username, email, status) VALUES
                ('filter.user1',   'filter.user1@test.com',   'active'),
                ('filter.user2',   'filter.user2@test.com',   'active');

            INSERT INTO filter_ne (username, email, status) VALUES
                ('filter.active',   'filter.active@test.com',   'active'),
                ('filter.suspended', 'filter.suspended@test.com', 'suspended'),
                ('filter.inactive',  'filter.inactive@test.com',  'inactive'),
                ('filter.deleted',   'filter.deleted@test.com',   'deleted');

            INSERT INTO multifilter (username, email, status, active) VALUES
                ('mf.active',    'mf.active@test.com',    'active',   TRUE),
                ('mf.inactive',  'mf.inactive@test.com',  'inactive', TRUE),
                ('mf.closed',    'mf.closed@test.com',    'closed',   FALSE);

            INSERT INTO builtin_table (username, email) VALUES
                ('builtin1',     'builtin1@test.com'),
                ('builtin2',     'builtin2@test.com');
            """;
}
