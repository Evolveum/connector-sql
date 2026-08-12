/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.test.SqlIntegrationTestBase;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for filter-based search operations.
 * Tests all supported filter types and verifies returned data is correct.
 */
@Test(singleThreaded = true)
public class SqlSearchFilterTest
        extends SqlIntegrationTestBase<SqlSearchFilterTest.TestConnector> {

    protected static class TestConnector extends DefaultTestConnector {
        protected TestConnector() { super(); }
    }

    @Override
    protected String schemaSql() {
        return """
                DROP TABLE IF EXISTS app_user CASCADE; DROP TABLE IF EXISTS app_group CASCADE;
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

    @Override
    protected String dataSql() {
        return """
                INSERT INTO app_group (id, name, description) VALUES
                    (1, 'devs', 'Software developers'),
                    (2, 'admins', 'System administrators'),
                    (3, 'qa', 'Quality assurance testers'),
                    (4, 'managers', 'Project managers'),
                    (5, 'interns', 'Intern volunteers');
                INSERT INTO app_user (id, username, email, age, created_at, is_active) VALUES
                    (1, 'john.doe', 'john@company.com', 30, '2024-01-15 10:00:00', true),
                    (2, 'jane.smith', 'jane@company.com', 28, '2024-02-20 11:00:00', true),
                    (3, 'bob.wilson', 'bob@company.com', 35, '2024-03-10 09:00:00', false),
                    (4, 'alice.jones', 'alice@company.com', 25, '2024-04-05 14:00:00', true),
                    (5, 'charlie.brown', 'charlie@company.com', 22, '2024-05-12 16:00:00', true);
                """;
    }

    @Override
    protected void initConnector() {
        connector = new TestConnector();
        connector.init(defaultConfig());
    }

    // ─── UID and Name filters ───

    @Test
    public void testUidFilter() throws Exception {
        Filter uidFilter = FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, "1"));
        List<ConnectorObject> results = search("app_user", uidFilter);
        assertThat(results).hasSize(1);
        assertThat(getAttr(results.getFirst(), "USERNAME")).isEqualTo("john.doe");
    }

    @Test
    public void testUidFilterNoMatch() throws Exception {
        Filter uidFilter = FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, "99"));
        assertThat(search("app_user", uidFilter)).isEmpty();
    }

    @Test
    public void testNameFilter() throws Exception {
        Filter nameFilter = FilterBuilder.equalTo(AttributeBuilder.build("USERNAME", "alice.jones"));
        assertThat(search("app_user", nameFilter)).hasSize(1);
    }

    @Test
    public void testNameFilterNoMatch() throws Exception {
        assertThat(search("app_user", FilterBuilder.equalTo(
                AttributeBuilder.build("USERNAME", "nobody")))).isEmpty();
    }

    // ─── Equals filter (parameterized by type) ───

    @DataProvider
    public static Object[][] equalsFilterProvider() {
        return new Object[][]{
                {"equals-string", FilterBuilder.equalTo(AttributeBuilder.build("EMAIL", "john@company.com")), 1},
                {"equals-string-no-match", FilterBuilder.equalTo(AttributeBuilder.build("EMAIL", "nonexistent@test.com")), 0},
                {"equals-boolean", FilterBuilder.equalTo(AttributeBuilder.build("IS_ACTIVE", true)), 4},
                {"equals-integer", FilterBuilder.equalTo(AttributeBuilder.build("AGE", 30)), 1},
                {"equals-no-match", FilterBuilder.equalTo(AttributeBuilder.build("EMAIL", "no@test.com")), 0},
        };
    }

    @Test(dataProvider = "equalsFilterProvider")
    public void testEqualsFilter(String name, Filter filter, int expectedCount) throws Exception {
        assertThat(search("app_user", filter)).hasSize(expectedCount);
    }

    // ─── String pattern filters ───

    @DataProvider
    public static Object[][] stringPatternFilterProvider() {
        return new Object[][]{
                {"contains", FilterBuilder.contains(AttributeBuilder.build("USERNAME", "john")), 1},
                {"startsWith", FilterBuilder.startsWith(AttributeBuilder.build("EMAIL", "john")), 1},
                {"endsWith", FilterBuilder.endsWith(AttributeBuilder.build("EMAIL", "@company.com")), 5},
        };
    }

    @Test(dataProvider = "stringPatternFilterProvider")
    public void testStringPatternFilter(String name, Filter filter, int expectedCount) throws Exception {
        initConnector();
        List<ConnectorObject> results = search("app_user", filter);
        assertThat(results).hasSize(expectedCount);
    }

    // ─── Numeric comparison filters ───

    @DataProvider
    public static Object[][] numericComparisonFilterProvider() {
        return new Object[][]{
                {"greaterThan", FilterBuilder.greaterThan(AttributeBuilder.build("AGE", 25)), 3},
                {"greaterThanOrEqual", FilterBuilder.greaterThanOrEqualTo(AttributeBuilder.build("AGE", 30)), 2},
                {"lessThan", FilterBuilder.lessThan(AttributeBuilder.build("AGE", 28)), 2},
                {"lessThanOrEqual", FilterBuilder.lessThanOrEqualTo(AttributeBuilder.build("AGE", 28)), 3},
        };
    }

    @Test(dataProvider = "numericComparisonFilterProvider")
    public void testNumericComparisonFilter(String name, Filter filter, int expectedCount) throws Exception {
        initConnector();
        List<ConnectorObject> results = search("app_user", filter);
        assertThat(results).hasSize(expectedCount);
    }

    // ─── Compound filters (AND, OR) ───

    @Test
    public void testAndFilter() throws Exception {
        Filter andFilter = FilterBuilder.and(
                FilterBuilder.equalTo(AttributeBuilder.build("AGE", 28)),
                FilterBuilder.equalTo(AttributeBuilder.build("IS_ACTIVE", true))
        );
        List<ConnectorObject> results = search("app_user", andFilter);
        assertThat(results).hasSize(1);
        assertThat(getAttr(results.getFirst(), "USERNAME")).isEqualTo("jane.smith");
    }

    @Test
    public void testAndFilterNoMatch() throws Exception {
        Filter andFilter = FilterBuilder.and(
                FilterBuilder.equalTo(AttributeBuilder.build("AGE", 28)),
                FilterBuilder.equalTo(AttributeBuilder.build("USERNAME", "bob.wilson"))
        );
        assertThat(search("app_user", andFilter)).isEmpty();
    }

    @Test
    public void testOrFilter() throws Exception {
        Filter orFilter = FilterBuilder.or(
                FilterBuilder.equalTo(AttributeBuilder.build("AGE", 22)),
                FilterBuilder.equalTo(AttributeBuilder.build("AGE", 35))
        );
        List<ConnectorObject> results = search("app_user", orFilter);
        assertThat(results).hasSize(2);
    }

    @Test
    public void testComplexNestedFilter() throws Exception {
        Filter complexFilter = FilterBuilder.and(
                FilterBuilder.and(
                        FilterBuilder.greaterThan(AttributeBuilder.build("AGE", 24)),
                        FilterBuilder.lessThan(AttributeBuilder.build("AGE", 33))
                ),
                FilterBuilder.equalTo(AttributeBuilder.build("IS_ACTIVE", true))
        );
        List<ConnectorObject> results = search("app_user", complexFilter);
        assertThat(results).hasSize(3);
        assertThat(results).extracting(o -> getAttr(o, "USERNAME"))
                .contains("john.doe", "jane.smith", "alice.jones");
    }

    // ─── NOT filter ───

    @Test
    public void testNotFilter() throws Exception {
        Filter notFilter = FilterBuilder.not(
                FilterBuilder.equalTo(AttributeBuilder.build("AGE", 30))
        );
        assertThat(search("app_user", notFilter)).hasSize(4);
    }

    @Test
    public void testNotFilterWithAnd() throws Exception {
        Filter notFilter = FilterBuilder.not(
                FilterBuilder.startsWith(AttributeBuilder.build("USERNAME", "j"))
        );
        List<ConnectorObject> results = search("app_user", notFilter);
        assertThat(results).hasSize(3);
        for (ConnectorObject obj : results) {
            var username = (String) getAttr(obj, "USERNAME");
            assertThat(username.startsWith("j")).isFalse();
        }
    }

    // ─── Multiple object class filters ───

    @Test
    public void testFilterOnGroupTable() throws Exception {
        Filter nameFilter = FilterBuilder.startsWith(AttributeBuilder.build("NAME", "dev"));
        assertThat(search("app_group", nameFilter)).hasSize(1);
    }

    @Test
    public void testFilterOnGroupTableNoMatch() throws Exception {
        assertThat(search("app_group", FilterBuilder.equalTo(
                AttributeBuilder.build("NAME", "nonexistent")))).isEmpty();
    }

    // ─── Verify returned data integrity ───

    @Test
    public void testSearchWithFilterReturnsCompleteAttributes() throws Exception {
        List<ConnectorObject> results = search("app_user", FilterBuilder.contains(
                AttributeBuilder.build("EMAIL", "company")));
        assertThat(results).hasSize(5);
        for (ConnectorObject obj : results) {
            assertThat(obj.getUid()).isNotNull();
            assertThat(obj.getUid().getValue()).isNotNull();
            assertThat(obj.getName()).isNotNull();
        }
    }

    // ─── Datetime filter tests ───

    static ZonedDateTime toZdt(String s) {
        return Timestamp.valueOf(s).toInstant().atZone(ZoneId.systemDefault());
    }

    @DataProvider
    public static Object[][] timestampFilterProvider() {
        return new Object[][]{
                {"timestamp-equals",
                        FilterBuilder.equalTo(AttributeBuilder.build("CREATED_AT", toZdt("2024-01-15 10:00:00"))), 1},
                {"timestamp-greaterThan",
                        FilterBuilder.greaterThan(AttributeBuilder.build("CREATED_AT", toZdt("2024-03-01 00:00:00"))), 3},
                {"timestamp-lessThan",
                        FilterBuilder.lessThan(AttributeBuilder.build("CREATED_AT", toZdt("2024-03-01 00:00:00"))), 2},
                {"timestamp-lessThanOrEqual",
                        FilterBuilder.lessThanOrEqualTo(AttributeBuilder.build("CREATED_AT", toZdt("2024-04-05 14:00:00"))), 4},
        };
    }

    @Test(dataProvider = "timestampFilterProvider")
    public void testTimestampFilter(String name, Filter filter, int expectedCount) throws Exception {
        assertThat(search("app_user", filter)).hasSize(expectedCount);
    }

    @Test
    public void testBetweenFilterOnTimestamp() throws Exception {
        Filter betweenFilter = FilterBuilder.and(
                FilterBuilder.greaterThanOrEqualTo(AttributeBuilder.build("CREATED_AT", toZdt("2024-02-20 11:00:00"))),
                FilterBuilder.lessThanOrEqualTo(AttributeBuilder.build("CREATED_AT", toZdt("2024-04-05 14:00:00")))
        );
        List<ConnectorObject> results = search("app_user", betweenFilter);
        assertThat(results).hasSize(3);
        assertThat(results).extracting(o -> getAttr(o, "USERNAME"))
                .contains("jane.smith", "bob.wilson", "alice.jones");
    }

    @Test
    public void testComplexFilterWithTimestampAndBoolean() throws Exception {
        Filter complexFilter = FilterBuilder.and(
                FilterBuilder.greaterThan(AttributeBuilder.build("CREATED_AT", toZdt("2024-03-01 00:00:00"))),
                FilterBuilder.equalTo(AttributeBuilder.build("IS_ACTIVE", true))
        );
        List<ConnectorObject> results = search("app_user", complexFilter);
        assertThat(results).hasSize(2);
        assertThat(results).extracting(o -> getAttr(o, "USERNAME"))
                .contains("alice.jones", "charlie.brown");
    }
}
