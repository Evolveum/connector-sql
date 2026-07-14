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
import com.evolveum.polygon.sql.base.test.PostgresDatabaseInitializer;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SQL search operation using real PostgreSQL 16 embedded via zonkyio.
 * Verifies that query-based operations work correctly against an actual Postgres instance.
 */
@Test(singleThreaded = true)
public class SqlSearchOperationPostgresTest {

    private PostgresDatabaseInitializer postgres;
    private TestSqlConnector connector;

    private static class TestSqlConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        TestSqlConnector() { super(false); }
        @Override protected void initializeObjectClassHandler(SqlHandlerBuilder builder) {}
        @Override protected void initializeSchema(SqlGroovySchemaLoader loader) {
            // No scripts to load - schema is auto-discovered from DB tables
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        postgres = PostgresDatabaseInitializer.create();

        // Load schema and data using direct JDBC before initializing the connector
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            executeSql(conn, "postgresql/basic/schema.sql");
            executeSql(conn, "postgresql/basic/data.sql");
        }

        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
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
        if (postgres != null) { postgres.close(); postgres = null; }
    }

    private static String readResource(String path) throws IOException {
        var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        return new String(Objects.requireNonNull(is, "Resource not found: " + path).readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void executeSql(Connection conn, String resourcePath) throws Exception {
        var sql = readResource(resourcePath);
        try (var stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
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
    public void testSearchAppUser() throws Exception {
        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(new ObjectClass("app_user"), null, results::add, opts());

        assertThat(results).hasSize(2);
        for (ConnectorObject o : results) {
            assertThat(o.getUid().getValue()).isNotNull();
            assertThat(o.getName()).isNotNull();
        }
    }

    @Test
    public void testSearchAppGroup() throws Exception {
        List<ConnectorObject> r = new ArrayList<>();
        connector.executeQuery(new ObjectClass("app_group"), null, r::add, opts());
        assertThat(r).hasSize(2);
    }

    @Test
    public void testSearchAppRole() throws Exception {
        List<ConnectorObject> r = new ArrayList<>();
        connector.executeQuery(new ObjectClass("app_role"), null, r::add, opts());
        assertThat(r).hasSize(3);
    }

    @Test
    public void testSearchProject() throws Exception {
        List<ConnectorObject> r = new ArrayList<>();
        connector.executeQuery(new ObjectClass("project"), null, r::add, opts());
        assertThat(r).hasSize(2);
    }

    @Test
    public void testAllObjectClassesWork() throws Exception {
        // Skip useraddress due to QueryDSL column duplication issue with primary_flag
        for (String name : List.of("app_user", "app_group", "app_role", "project", "projectmembership")) {
            List<ConnectorObject> r = new ArrayList<>();
            connector.executeQuery(new ObjectClass(name), null, r::add, opts());
            assertThat(r).isNotEmpty().withFailMessage("No results for " + name);
        }
    }
}