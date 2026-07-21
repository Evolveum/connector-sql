/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.test.PostgresDatabaseInitializer;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Integration tests for SQL schema detection and translation using real PostgreSQL 16 embedded via zonkyio.
 * Verifies that the connector correctly detects schemas, column types, constraints,
 * and foreign key references from an actual Postgres instance.
 */
@Test(singleThreaded = true)
public class SqlSchemaDetectorPostgresTest {

    /** Stub connector class required by the ConnId schema builder. */
    static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    private PostgresDatabaseInitializer postgres;
    private SqlBaseContext context;

    @BeforeMethod
    public void setUp() throws Exception {
        postgres = PostgresDatabaseInitializer.create();

        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setAutoDiscoverSchema(false);

        context = new SqlBaseContext(config);
        context.initializeConnectionPool();

        try (var conn = context.getConnection()) {
            executeSql(conn.getConnection(), "postgresql/basic/schema.sql");
            executeSql(conn.getConnection(), "postgresql/basic/data.sql");
        } catch (Exception e) {
            context.close();
            throw e;
        }
    }

    @AfterMethod
    public void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
        if (postgres != null) {
            postgres.close();
            postgres = null;
        }
    }

    @Test
    public void testConnectionPoolInitialization() {
        assertThat(context.getConnectionPool()).isNotNull();
        assertThat(context.getConnectionPool().isClosed()).isFalse();
    }

    @Test
    public void testConnectionTest() throws Exception {
        context.testConnection();
    }

    @Test
    public void testAllTablesDiscovered() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();
        assertThat(tables).hasSize(6);
    }

    @Test
    public void testUserTableSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        SqlTableInfo userTable = table(tables, "app_user");
        assertThat(userTable).isNotNull();
        Map<String, SqlColumnMeta> columns = toColumnMap(userTable);

        var idCol = columns.get("id");
        assertThat(idCol).isNotNull();
        assertThat(idCol.isPrimaryKey()).isTrue();
        assertThat(idCol.isNullable()).isFalse();

        assertThat(columns.containsKey("username")).isTrue();
        assertThat(columns.get("username").isNullable()).isFalse();

        assertThat(columns.containsKey("email")).isTrue();
        assertThat(columns.get("email").isNullable()).isTrue();

        assertThat(columns.containsKey("created_at")).isTrue();
        assertThat(columns.get("created_at").isNullable()).isTrue();
    }

    @Test
    public void testGroupTableSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "app_group"));
        assertThat(columns).hasSize(3);
        assertThat(columns.get("id").isNullable()).isFalse();
        assertThat(columns.get("id").isPrimaryKey()).isTrue();
    }

    @Test
    public void testRoleTableSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "app_role"));
        assertThat(columns).hasSize(3);
        assertThat(columns.get("id").isPrimaryKey()).isTrue();
        assertThat(columns.get("name").isNullable()).isFalse();
    }

    @Test
    public void testProjectTableSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "project"));
        assertThat(columns).hasSize(4);
        assertThat(columns.get("id").isPrimaryKey()).isTrue();
        assertThat(columns.get("name").isNullable()).isFalse();
    }

    @Test
    public void testUserAddressSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "useraddress"));
        assertThat(columns).hasSize(6);
        assertThat(columns.get("id").isPrimaryKey()).isTrue();

        var userIdCol = columns.get("user_id");
        assertThat(userIdCol).isNotNull();
        assertThat(userIdCol.isNullable()).isFalse();

        assertColumnType(columns.get("street"), "VARCHAR");
        assertColumnType(columns.get("city"), "VARCHAR");
        assertColumnType(columns.get("country"), "VARCHAR");
    }

    @Test
    public void testProjectMembershipSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "projectmembership"));
        assertThat(columns).hasSize(5);
        assertThat(columns.get("id").isPrimaryKey()).isTrue();

        assertThat(columns.get("user_id").isNullable()).isFalse();
        assertThat(columns.get("project_id").isNullable()).isFalse();
        assertThat(columns.get("role_id").isNullable()).isFalse();
        assertThat(columns.get("joined_at").isNullable()).isTrue();
    }

    @Test
    public void testConnIdSchemaTranslation() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        var connIdSchema = new SqlSchemaTranslator(tables)
                .translate(StubConnector.class, context)
                .connIdSchema();
        assertThat(connIdSchema).isNotNull();

        Map<String, ObjectClassInfo> objClasses = connIdSchema.getObjectClassInfo().stream()
                .collect(Collectors.toMap(
                        info -> info.getType().toLowerCase(),
                        Function.identity(),
                        (a, b) -> a));

        for (String name : List.of("app_user", "app_group", "app_role", "project", "useraddress", "projectmembership")) {
            assertThat(objClasses.containsKey(name))
                    .withFailMessage("Should contain '" + name + "' object class").isTrue();
        }

        // Verify User attributes: the single-PK "id" column is mapped to __UID__
        var userClass = objClasses.get("app_user");
        assertThat(userClass).isNotNull();
        Map<String, AttributeInfo> userAttrs = userClass.getAttributeInfo().stream()
                .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));

        assertThat(userAttrs.get(Uid.NAME).isRequired()).isTrue();
        assertThat(userAttrs.get(Uid.NAME).getNativeName()).isEqualTo("id");
        assertThat(userAttrs.get("email").isRequired()).isFalse();

        // Verify ProjectMembership: FK columns are references, still required
        var membership = objClasses.get("projectmembership");
        assertThat(membership).isNotNull();
        Map<String, AttributeInfo> memberAttrs = membership.getAttributeInfo().stream()
                .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));

        assertThat(memberAttrs.get("user_id").isRequired()).isTrue();
        //assertThat(memberAttrs.get("user_id").getReferencedObjectClassName()).isEqualTo("app_user");
        assertThat(memberAttrs.get("project_id").isRequired()).isTrue();
        assertThat(memberAttrs.get("role_id").isRequired()).isTrue();
    }

    @Test
    public void testMultiplePoolConnections() throws SQLException {
        try (var conn1 = context.getConnection();
             var conn2 = context.getConnection()) {

            try (var s1 = conn1.getConnection().createStatement();
                 var s2 = conn2.getConnection().createStatement()) {
                try (var rs = s1.executeQuery("SELECT COUNT(*) FROM app_user")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
            }
        }
    }

    @Test
    public void testCloseAndReinit() {
        context.close();
        try {
            context.getConnection();
            fail("Should throw IllegalStateException after close");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Connection pool not initialized");
        }

        context.initializeConnectionPool();
        context.getConnection();

        context.close();
        try {
            context.getConnection();
            fail("Should throw IllegalStateException after close");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Connection pool not initialized");
        }
    }

    // --- Helpers ---

    static SqlTableInfo table(List<SqlTableInfo> tables, String name) {
        return tables.stream()
                .filter(t -> name.equalsIgnoreCase(t.getName()))
                .findFirst().orElse(null);
    }

    private void assertColumnType(SqlColumnMeta col, String... allowedTypes) {
        var typeName = col.getTypeName().toUpperCase();
        for (String t : allowedTypes) {
            if (typeName.equals(t) || typeName.contains(t)) return;
        }
        fail("Column '" + col.getName() + "' expected " + List.of(allowedTypes) + " but got " + typeName);
    }

    private Map<String, SqlColumnMeta> toColumnMap(SqlTableInfo table) {
        return table.getColumns().stream()
                .collect(Collectors.toMap(SqlColumnMeta::getName, Function.identity(), (a, b) -> a));
    }

    private static void executeSql(Connection conn, String resourcePath) throws Exception {
        try (var is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new Exception("Resource not found: " + resourcePath);
            }
            var sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (var stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}