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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SQL search operation using H2 embedded database in MySQL mode.
 * These tests verify QueryDSL-based search works correctly with H2's case-sensitivity handling.
 */
@Test(singleThreaded = true)
public class SqlSearchOperationIntegrationTest {

    private static final String URL = "jdbc:h2:mem:searchtest8;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private TestSqlConnector connector;

    private static class TestSqlConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        TestSqlConnector() { super(false); }
        @Override
        protected void initializeObjectClassHandler(SqlHandlerBuilder builder) {}

        @Override
        protected void initializeSchema(SqlGroovySchemaLoader loader) {
            // No scripts to load - schema is auto-discovered from DB tables
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute(readResource("h2/basic/search_schema.sql"));
            stmt.execute("COMMIT");
            stmt.execute(readResource("h2/basic/search_data.sql"));
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

        connector = new TestSqlConnector();
        connector.init(config);
    }

    @AfterMethod
    public void tearDown() {
        if (connector != null) { connector.dispose(); connector = null; }
    }

    private static String readResource(String path) throws IOException {
        var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (is == null) throw new IllegalArgumentException("Resource not found: " + path);
        return new String(is.readAllBytes());
    }

    private OperationOptions opts() {
        return new OperationOptions(Collections.emptyMap());
    }

    @Test
    public void testSchemaContainsDiscoveredObjectClasses() throws Exception {
        var schema = connector.schema();
        assertThat(schema.getObjectClassInfo()).isNotEmpty();
        List<String> names = schema.getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType).map(String::toLowerCase).toList();
        assertThat(names).contains(
                "app_user", "app_group", "app_role", "project", "useraddress", "projectmembership");
    }

    @Test
    public void testSearchWithUnqualifiedPaths() throws Exception {
        // This test verifies that unqualified column paths work (key fix for H2 MySQL mode)
        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(new ObjectClass("app_user"), null, results::add, opts());

        assertThat(results).hasSize(2);
        for (ConnectorObject o : results) {
            assertThat(o.getUid().getValue()).isNotNull();
            assertThat(o.getName()).isNotNull();
        }
    }

    @Test
    public void testSearchGroups() throws Exception {
        List<ConnectorObject> r = new ArrayList<>();
        connector.executeQuery(new ObjectClass("app_group"), null, r::add, opts());
        assertThat(r).hasSize(2);
    }

    @Test
    public void testSearchRoles() throws Exception {
        List<ConnectorObject> r = new ArrayList<>();
        connector.executeQuery(new ObjectClass("app_role"), null, r::add, opts());
        assertThat(r).hasSize(3);
    }

    @Test
    public void testSearchProjects() throws Exception {
        List<ConnectorObject> r = new ArrayList<>();
        connector.executeQuery(new ObjectClass("project"), null, r::add, opts());
        assertThat(r).hasSize(2);
    }

    @Test
    public void testAllObjectClassesWork() throws Exception {
        for (String name : List.of("app_user", "app_group", "app_role", "project", "useraddress", "projectmembership")) {
            List<ConnectorObject> r = new ArrayList<>();
            connector.executeQuery(new ObjectClass(name), null, r::add, opts());
            assertThat(r).isNotEmpty().withFailMessage("No results for " + name);
        }
    }
}
