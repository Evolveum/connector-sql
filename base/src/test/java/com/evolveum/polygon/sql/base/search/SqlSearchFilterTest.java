/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.groovy.SqlGroovySchemaLoader;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerBuilder;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.identityconnectors.framework.common.objects.Uid.NAME;

/**
 * Integration tests for filter-based search operations.
 * Tests all supported filter types and verifies returned data is correct.
 */
@Test(singleThreaded = true)
public class SqlSearchFilterTest {

    private static final String URL = """
            jdbc:h2:mem:filtertest;\
            DB_CLOSE_DELAY=-1;MODE=MySQL""";

    private TestConnector connector;

    private static class TestConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        TestConnector() { super(false); }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerBuilder builder) {
            // Use default handlers
        }

        @Override
        protected void initializeSchema(SqlGroovySchemaLoader loader) {
            // Auto-discover schema from tables
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute(createSchemaSql());
            stmt.execute("COMMIT");
            stmt.execute(insertDataSql());
            stmt.execute("COMMIT");
        }

        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(URL);
        config.setUsername("sa");
        config.setPassword("");
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setAutoDiscoverSchema(true);
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

    private static String readResource(String path) throws IOException {
        var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (is == null) throw new IllegalArgumentException("Resource not found: " + path);
        return new String(is.readAllBytes());
    }

    private OperationOptions opts() {
        return new OperationOptions(Collections.emptyMap());
    }

    private ObjectClass userOcl() {
        return new ObjectClass("app_user");
    }

    private ObjectClass groupOcl() {
        return new ObjectClass("app_group");
    }

    /**
     * Create the test schema with user and group tables.
     */
    private String createSchemaSql() {
        return """
                DROP TABLE IF EXISTS app_user; DROP TABLE IF EXISTS app_group;
                CREATE TABLE app_user (
                id INT PRIMARY KEY AUTO_INCREMENT, 
                username VARCHAR(255) NOT NULL,
                email VARCHAR(255),
                age INT,
                created_at TIMESTAMP,
                is_active BOOLEAN);
                CREATE TABLE app_group (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(255) NOT NULL,
                description VARCHAR(1024));
                """;
    }

    /**
     * Insert test data with known values for filter testing.
     */
    private String insertDataSql() {
        return """
                INSERT INTO app_group (id, name, description) VALUES \
                (1, 'devs', 'Software developers'), \
                (2, 'admins', 'System administrators'), \
                (3, 'qa', 'Quality assurance testers'), \
                (4, 'managers', 'Project managers'), \
                (5, 'interns', 'Intern volunteers'); \
                INSERT INTO app_user (id, username, email, age, created_at, is_active) VALUES \
                (1, 'john.doe', 'john@company.com', 30, '2024-01-15 10:00:00', true), \
                (2, 'jane.smith', 'jane@company.com', 28, '2024-02-20 11:00:00', true), \
                (3, 'bob.wilson', 'bob@company.com', 35, '2024-03-10 09:00:00', false), \
                (4, 'alice.jones', 'alice@company.com', 25, '2024-04-05 14:00:00', true), \
                (5, 'charlie.brown', 'charlie@company.com', 22, '2024-05-12 16:00:00', true);\
                """;
    }

    /**
     * Collect all results from a search query.
     */
    private List<ConnectorObject> executeSearch(ObjectClass ocl, Filter filter) throws Exception {
        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(ocl, filter, results::add, opts());
        return results;
    }

    /**
     * Get an attribute value from a connector object, or null if not present.
     */
    private Object getAttribute(ConnectorObject obj, String attrName) {
        var attr = obj.getAttributeByName(attrName);
        return (attr != null && !attr.getValue().isEmpty()) ?
                attr.getValue().getFirst() : null;
    }

    // ─── UID and Name filters ─────────────────────────────────────────

    @Test
    public void testUidFilter() throws Exception {
        Filter uidFilter = FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, "1"));
        List<ConnectorObject> results = executeSearch(userOcl(), uidFilter);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUid().getValue()).isNotNull();
        var uidAttr = results.getFirst().getAttributeByName(Uid.NAME);
        if (uidAttr != null && !uidAttr.getValue().isEmpty()) {
            var val = uidAttr.getValue().getFirst();
            assertThat(val.toString()).isEqualTo("1");
        }
        assertThat(getAttribute(results.getFirst(), "username")).isEqualTo("john.doe");
    }

    @Test
    public void testUidFilterNoMatch() throws Exception {
        Filter uidFilter = FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, "99"));
        List<ConnectorObject> results = executeSearch(userOcl(), uidFilter);

        assertThat(results).isEmpty();
    }

    @Test
    public void testNameFilter() throws Exception {
        Filter nameFilter = FilterBuilder.equalTo(AttributeBuilder.build("username", "alice.jones"));
        List<ConnectorObject> results = executeSearch(userOcl(), nameFilter);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUid().getValue()).isNotNull();
        var uidAttr = results.getFirst().getAttributeByName(Uid.NAME);
        if (uidAttr != null && !uidAttr.getValue().isEmpty()) {
            assertThat(uidAttr.getValue().getFirst().toString()).isEqualTo("4");
        }
    }

    @Test
    public void testNameFilterNoMatch() throws Exception {
        Filter nameFilter = FilterBuilder.equalTo(AttributeBuilder.build("username", "nobody"));
        List<ConnectorObject> results = executeSearch(userOcl(), nameFilter);

        assertThat(results).isEmpty();
    }

    // ─── Equals filter ──────────────────────────────────────────────────

    @Test
    public void testEqualsFilter() throws Exception {
        Filter emailFilter = FilterBuilder.equalTo(AttributeBuilder.build("email", "john@company.com"));
        List<ConnectorObject> results = executeSearch(userOcl(), emailFilter);

        assertThat(results).hasSize(1);
        assertThat(getAttribute(results.getFirst(), "email")).isEqualTo("john@company.com");
        assertThat(getAttribute(results.getFirst(), "username")).isEqualTo("john.doe");
    }

    @Test
    public void testEqualsFilterNoMatch() throws Exception {
        Filter emailFilter = FilterBuilder.equalTo(AttributeBuilder.build("email", "nonexistent@test.com"));
        List<ConnectorObject> results = executeSearch(userOcl(), emailFilter);

        assertThat(results).isEmpty();
    }

    @Test
    public void testEqualsFilterOnBoolean() throws Exception {
        Filter activeFilter = FilterBuilder.equalTo(AttributeBuilder.build("is_active", true));
        List<ConnectorObject> results = executeSearch(userOcl(), activeFilter);

        assertThat(results).hasSize(4);
        for (ConnectorObject obj : results) {
            var active = (Boolean) getAttribute(obj, "is_active");
            assertThat(active).isTrue();
        }
    }

    @Test
    public void testEqualsFilterOnInteger() throws Exception {
        Filter ageFilter = FilterBuilder.equalTo(AttributeBuilder.build("age", 30));
        List<ConnectorObject> results = executeSearch(userOcl(), ageFilter);

        assertThat(results).hasSize(1);
        assertThat(getAttribute(results.getFirst(), "age")).isEqualTo(30);
        assertThat(getAttribute(results.getFirst(), "username")).isEqualTo("john.doe");
    }

    // ─── String pattern filters ─────────────────────────────────────────

    @Test
    public void testContainsFilter() throws Exception {
        Filter containsFilter = FilterBuilder.contains(AttributeBuilder.build("username", "john"));
        List<ConnectorObject> results = executeSearch(userOcl(), containsFilter);

        assertThat(results).hasSize(1);
        for (ConnectorObject obj : results) {
            var username = (String) getAttribute(obj, "username");
            assertThat(username).contains("john");
        }
        assertThat(results).extracting(o -> getAttribute(o, "username"))
                .contains("john.doe");
    }

    @Test
    public void testStartsWithFilter() throws Exception {
        Filter startFilter = FilterBuilder.startsWith(AttributeBuilder.build("email", "john"));
        List<ConnectorObject> results = executeSearch(userOcl(), startFilter);

        assertThat(results).hasSize(1);
        var uidAttr = results.getFirst().getAttributeByName(Uid.NAME);
        if (uidAttr != null && !uidAttr.getValue().isEmpty()) {
            assertThat(uidAttr.getValue().getFirst().toString()).isEqualTo("1");
        }
        assertThat((String) getAttribute(results.getFirst(), "email")).startsWith("john");
    }

    @Test
    public void testEndsWithFilter() throws Exception {
        Filter endFilter = FilterBuilder.endsWith(AttributeBuilder.build("email", "@company.com"));
        List<ConnectorObject> results = executeSearch(userOcl(), endFilter);

        assertThat(results).hasSize(5);
    }

    // ─── Numeric comparison filters ─────────────────────────────────────

    @Test
    public void testGreaterThanFilter() throws Exception {
        Filter greaterFilter = FilterBuilder.greaterThan(AttributeBuilder.build("age", 25));
        List<ConnectorObject> results = executeSearch(userOcl(), greaterFilter);

        assertThat(results).hasSize(3);
        for (ConnectorObject obj : results) {
            var age = (Integer) getAttribute(obj, "age");
            assertThat(age).isGreaterThan(25);
        }
    }

    @Test
    public void testGreaterThanOrEqualFilter() throws Exception {
        Filter geFilter = FilterBuilder.greaterThanOrEqualTo(AttributeBuilder.build("age", 30));
        List<ConnectorObject> results = executeSearch(userOcl(), geFilter);

        assertThat(results).hasSize(2);
        for (ConnectorObject obj : results) {
            var age = (Integer) getAttribute(obj, "age");
            assertThat(age).isGreaterThanOrEqualTo(30);
        }
    }

    @Test
    public void testLessThanFilter() throws Exception {
        Filter ltFilter = FilterBuilder.lessThan(AttributeBuilder.build("age", 28));
        List<ConnectorObject> results = executeSearch(userOcl(), ltFilter);

        assertThat(results).hasSize(2);
        for (ConnectorObject obj : results) {
            var age = (Integer) getAttribute(obj, "age");
            assertThat(age).isLessThan(28);
        }
    }

    @Test
    public void testLessThanOrEqualFilter() throws Exception {
        Filter leFilter = FilterBuilder.lessThanOrEqualTo(AttributeBuilder.build("age", 28));
        List<ConnectorObject> results = executeSearch(userOcl(), leFilter);

        assertThat(results).hasSize(3);
        for (ConnectorObject obj : results) {
            var age = (Integer) getAttribute(obj, "age");
            assertThat(age).isLessThanOrEqualTo(28);
        }
    }

    // ─── Compound filters (AND, OR) ────────────────────────────────────

    @Test
    public void testAndFilter() throws Exception {
        Filter andFilter = FilterBuilder.and(
                FilterBuilder.equalTo(AttributeBuilder.build("age", 28)),
                FilterBuilder.equalTo(AttributeBuilder.build("is_active", true))
        );
        List<ConnectorObject> results = executeSearch(userOcl(), andFilter);

        assertThat(results).hasSize(1);
        var uidAttr = results.getFirst().getAttributeByName(Uid.NAME);
        if (uidAttr != null && !uidAttr.getValue().isEmpty()) {
            assertThat(uidAttr.getValue().getFirst().toString()).isEqualTo("2");
        }
        assertThat(getAttribute(results.getFirst(), "username")).isEqualTo("jane.smith");
    }

    @Test
    public void testAndFilterNoMatch() throws Exception {
        Filter andFilter = FilterBuilder.and(
                FilterBuilder.equalTo(AttributeBuilder.build("age", 28)),
                FilterBuilder.equalTo(AttributeBuilder.build("username", "bob.wilson"))
        );
        List<ConnectorObject> results = executeSearch(userOcl(), andFilter);

        assertThat(results).isEmpty();
    }

    @Test
    public void testOrFilter() throws Exception {
        Filter orFilter = FilterBuilder.or(
                FilterBuilder.equalTo(AttributeBuilder.build("age", 22)),
                FilterBuilder.equalTo(AttributeBuilder.build("age", 35))
        );
        List<ConnectorObject> results = executeSearch(userOcl(), orFilter);

        assertThat(results).hasSize(2);
        for (ConnectorObject obj : results) {
            var age = (Integer) getAttribute(obj, "age");
            assertThat(age).isIn(22, 35);
        }
    }

    @Test
    public void testComplexNestedFilter() throws Exception {
        // (age > 24 AND age < 33) AND is_active = true
        Filter rangeFilter = FilterBuilder.and(
                FilterBuilder.greaterThan(AttributeBuilder.build("age", 24)),
                FilterBuilder.lessThan(AttributeBuilder.build("age", 33))
        );
        Filter activeFilter = FilterBuilder.equalTo(AttributeBuilder.build("is_active", true));
        Filter complexFilter = FilterBuilder.and(rangeFilter, activeFilter);

        List<ConnectorObject> results = executeSearch(userOcl(), complexFilter);

        assertThat(results).hasSize(3);
        for (ConnectorObject obj : results) {
            var age = (Integer) getAttribute(obj, "age");
            var active = (Boolean) getAttribute(obj, "is_active");
            assertThat(age).isGreaterThan(24).isLessThan(33);
            assertThat(active).isTrue();
        }
        assertThat(results).extracting(o -> getAttribute(o, "username"))
                .contains("john.doe", "jane.smith", "alice.jones");
    }

    // ─── NOT filter ────────────────────────────────────────────────────

    @Test
    public void testNotFilter() throws Exception {
        Filter notFilter = FilterBuilder.not(
                FilterBuilder.equalTo(AttributeBuilder.build("age", 30))
        );
        List<ConnectorObject> results = executeSearch(userOcl(), notFilter);

        assertThat(results).hasSize(4);
        for (ConnectorObject obj : results) {
            var age = (Integer) getAttribute(obj, "age");
            assertThat(age).isNotEqualTo(30);
        }
    }

    @Test
    public void testNotFilterWithAnd() throws Exception {
        // NOT (username starts with 'j')
        Filter startsWithJ = FilterBuilder.startsWith(AttributeBuilder.build("username", "j"));
        Filter notFilter = FilterBuilder.not(startsWithJ);
        List<ConnectorObject> results = executeSearch(userOcl(), notFilter);

        assertThat(results).hasSize(3);
        for (ConnectorObject obj : results) {
            var username = (String) getAttribute(obj, "username");
            assertThat(username).isNotNull();
            assertThat(username.startsWith("j")).isFalse();
        }
    }

    // ─── Multiple object class filters ──────────────────────────────────

    @Test
    public void testFilterOnGroupTable() throws Exception {
        Filter nameFilter = FilterBuilder.startsWith(AttributeBuilder.build("name", "dev"));
        List<ConnectorObject> results = executeSearch(groupOcl(), nameFilter);

        assertThat(results).hasSize(1);
        var uidAttr = results.getFirst().getAttributeByName(Uid.NAME);
        if (uidAttr != null && !uidAttr.getValue().isEmpty()) {
            assertThat(uidAttr.getValue().getFirst().toString()).isEqualTo("1");
        }
        var name = (String) getAttribute(results.getFirst(), "name");
        assertThat(name).startsWith("dev");
    }

    @Test
    public void testFilterOnGroupTableNoMatch() throws Exception {
        Filter nameFilter = FilterBuilder.equalTo(AttributeBuilder.build("name", "nonexistent"));
        List<ConnectorObject> results = executeSearch(groupOcl(), nameFilter);

        assertThat(results).isEmpty();
    }

    // ─── Verify returned data integrity ─────────────────────────────────

    @Test
    public void testSearchWithFilterReturnsCompleteAttributes() throws Exception {
        Filter emailFilter = FilterBuilder.contains(AttributeBuilder.build("email", "company"));
        List<ConnectorObject> results = executeSearch(userOcl(), emailFilter);

        assertThat(results).hasSize(5);

        for (ConnectorObject obj : results) {
            assertThat(obj.getUid()).isNotNull();
            assertThat(obj.getUid().getValue()).isNotNull();
            assertThat(obj.getName()).isNotNull();

            Set<Attribute> attrs = obj.getAttributes();
            assertThat(attrs).isNotEmpty();
            assertThat(attrs).extracting(Attribute::getName)
                    .contains("username", "email", "age", "is_active");
        }
    }

    @Test
    public void testFilterOnlyReturnsFilteringAttributes() throws Exception {
        Filter ageFilter = FilterBuilder.greaterThan(AttributeBuilder.build("age", 25));
        List<ConnectorObject> results = executeSearch(userOcl(), ageFilter);

        assertThat(results).hasSize(3);

        for (ConnectorObject obj : results) {
            var age = (Integer) getAttribute(obj, "age");
            assertThat(age).isGreaterThan(25);
            assertThat(age).isIn(28, 30, 35);
        }
    }
}