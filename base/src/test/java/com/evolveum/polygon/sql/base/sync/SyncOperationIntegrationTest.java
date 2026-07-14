/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync;

import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.groovy.SqlGroovySchemaLoader;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerBuilder;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.*;

import java.sql.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Test(singleThreaded = true)
public class SyncOperationIntegrationTest {

    private static final String URL = "jdbc:h2:mem:synctest;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private TestSqlConnector connector;

    private static class TestSqlConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        TestSqlConnector() { super(false); }
        @Override
        protected void initializeSchema(SqlGroovySchemaLoader loader) {}
        @Override
        protected void initializeObjectClassHandler(SqlHandlerBuilder builder) {}
    }

    @BeforeMethod
    public void setUp() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS sync_test");
            stmt.execute("CREATE TABLE sync_test (" +
                "id INT PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(200), " +
                "updated_at BIGINT NOT NULL DEFAULT 0, deleted_at BIGINT)");
            stmt.execute("INSERT INTO sync_test VALUES (1, 'Alice', 'alice@example.com', 1000, NULL)");
            stmt.execute("INSERT INTO sync_test VALUES (2, 'Bob', 'bob@example.com', 1000, NULL)");
            stmt.execute("COMMIT");
        }
        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(URL);
        config.setUsername("sa");
        config.setPassword("");
        config.setPoolSize(5);
        config.setAutoDiscoverSchema(true);
        config.setDevelopmentMode(true);
        connector = new TestSqlConnector();
        connector.init(config);
    }

    @AfterMethod
    public void tearDown() {
        if (connector != null) { connector.dispose(); connector = null; }
    }

    @Test
    public void testSyncWithNullToken() throws Exception {
        // Verify raw JDBC works
        List<String> jdbcResults = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT updated_at, deleted_at FROM sync_test WHERE updated_at IS NOT NULL ORDER BY updated_at ASC LIMIT 200 OFFSET 0");
            while (rs.next()) {
                jdbcResults.add("updated_at=" + rs.getObject(1) + " deleted_at=" + rs.getObject(2));
            }
        }
        System.out.println("JDBC results: " + jdbcResults);
        assertThat(jdbcResults).hasSize(2);

        // Now verify sync works
        List<SyncDelta> deltas = new ArrayList<>();
        connector.sync(new ObjectClass("sync_test"), null,
            d -> { deltas.add(d); return true; },
            new OperationOptions(Collections.emptyMap()));
        System.out.println("Sync deltas: " + deltas.size());
        assertThat(deltas).hasSize(2);
    }

    @Test
    public void testSyncWithTokenFiltering() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE sync_test SET name = 'Bob Updated_Updated', updated_at = 5000 WHERE id = 2");
            stmt.execute("COMMIT");
        }

        List<SyncDelta> deltas = new ArrayList<>();
        connector.sync(new ObjectClass("sync_test"), new SyncToken(2000L),
            d -> { deltas.add(d); return true; },
            new OperationOptions(Collections.emptyMap()));
        System.out.println("Token-filtered sync deltas: " + deltas.size());
        assertThat(deltas).hasSize(1);
    }

    @Test
    public void testSyncDetectsSoftDelete() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE sync_test SET deleted_at = 5000, updated_at = 3000 WHERE id = 2");
            stmt.execute("COMMIT");
        }

        List<SyncDelta> deltas = new ArrayList<>();
        connector.sync(new ObjectClass("sync_test"), new SyncToken(2000L),
            d -> { deltas.add(d); return true; },
            new OperationOptions(Collections.emptyMap()));
        System.out.println("Soft-delete sync deltas: " + deltas.size());
        assertThat(deltas).isNotEmpty();
    }

    @Test
    public void testSyncWithNewRowAfterToken() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO sync_test (id, name, email, updated_at, deleted_at) " +
                "VALUES (3, 'Charlie', 'charlie@example.com', 5000, NULL)");
            stmt.execute("COMMIT");
        }

        List<SyncDelta> deltas = new ArrayList<>();
        connector.sync(new ObjectClass("sync_test"), new SyncToken(1000L),
            d -> { deltas.add(d); return true; },
            new OperationOptions(Collections.emptyMap()));
        System.out.println("New row sync deltas: " + deltas.size());
        assertThat(deltas).isNotEmpty();
    }
}
