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
import com.evolveum.polygon.sql.base.test.H2DatabaseInitializer;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Integration tests for SQL schema detection and translation using H2 embedded database.
 */
@Test(singleThreaded = true)
public class SqlSchemaDetectorIntegrationTest {

    /** Stub connector class required by the ConnId schema builder. */
    static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    private SqlBaseContext context;

    @BeforeMethod
    public void setUp() throws Exception {
        context = H2DatabaseInitializer.create();
    }

    @AfterMethod
    public void tearDown() {
        if (context != null) {
            context.close();
            context = null;
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

        assertThat(tables.size()).withFailMessage("Should discover exactly 6 tables").isEqualTo(6);
    }

    @Test
    public void testUserTableSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        SqlTableInfo userTable = table(tables, "user");
        assertThat(userTable).isNotNull();
        Map<String, SqlColumnMeta> columns = toColumnMap(userTable);

        var idCol = columns.get("id");
        assertThat(idCol).isNotNull();
        assertThat(idCol.isPrimaryKey()).isTrue();
        assertThat(idCol.isNullable()).isFalse();
        assertThat(idCol.isAutoIncrement()).isTrue();

        assertThat(columns.containsKey("username")).isTrue();
        assertThat(columns.get("username").isNullable()).isFalse();
        assertThat(columns.get("username").isUnique()).isTrue();

        assertThat(columns.containsKey("email")).isTrue();
        assertThat(columns.get("email").isNullable()).isTrue();
        assertThat(columns.get("email").isUnique()).isTrue();

        assertThat(columns.containsKey("created_at")).isTrue();
        assertThat(columns.get("created_at").isNullable()).isTrue();
    }

    @Test
    public void testGroupTableSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "group"));
        assertThat(columns.size()).isEqualTo(3);
        assertThat(columns.get("id").isNullable()).isFalse();
        assertThat(columns.get("id").isPrimaryKey()).isTrue();
    }

    @Test
    public void testRoleTableSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "role"));
        assertThat(columns.size()).isEqualTo(3);
        assertThat(columns.get("id").isPrimaryKey()).isTrue();
        assertThat(columns.get("name").isNullable()).isFalse();
    }

    @Test
    public void testProjectTableSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "project"));
        assertThat(columns.size()).isEqualTo(4);
        assertThat(columns.get("id").isPrimaryKey()).isTrue();
        assertThat(columns.get("name").isNullable()).isFalse();
    }

    @Test
    public void testUserAddressSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "useraddress"));
        assertThat(columns.size()).isEqualTo(6);
        assertThat(columns.get("id").isPrimaryKey()).isTrue();

        var userIdCol = columns.get("user_id");
        assertThat(userIdCol).isNotNull();
        assertThat(userIdCol.isNullable()).withFailMessage("UserAddress.user_id should be NOT NULL - UserAddress cannot exist without a User").isFalse();

        assertColumnType(columns.get("street"), "VARCHAR");
        assertColumnType(columns.get("city"), "VARCHAR");
        assertColumnType(columns.get("country"), "VARCHAR");
    }

    @Test
    public void testProjectMembershipSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "projectmembership"));
        assertThat(columns.size()).isEqualTo(5);
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
                        Function.identity()));

        for (String name : List.of("user", "group", "role", "project", "useraddress", "projectmembership")) {
            assertThat(objClasses.containsKey(name)).withFailMessage("Should contain '" + name + "' object class").isTrue();
        }

        // Verify User attributes: the single-PK "id" column is mapped to __UID__
        var userClass = objClasses.get("user");
        assertThat(userClass).isNotNull();
        Map<String, AttributeInfo> userAttrs = userClass.getAttributeInfo().stream()
                .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));
        assertThat(userAttrs.size()).isEqualTo(5);  // 4 columns + __NAME__ auto-added by ConnId

        assertThat(userAttrs.get(Uid.NAME).isRequired()).withFailMessage("User.id (__UID__) should be required").isTrue();
        assertThat(userAttrs.get(Uid.NAME).getNativeName()).isEqualTo("id");
        assertThat(userAttrs.get("email").isRequired()).withFailMessage("User.email should NOT be required").isFalse();

        // Verify ProjectMembership: FK columns are references, still required
        var membership = objClasses.get("projectmembership");
        assertThat(membership).isNotNull();
        Map<String, AttributeInfo> memberAttrs = membership.getAttributeInfo().stream()
                .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));
        assertThat(memberAttrs.size()).isEqualTo(6);  // 5 columns + __NAME__ auto-added by ConnId

        assertThat(memberAttrs.get("user_id").isRequired()).isTrue();
        assertThat(memberAttrs.get("user_id").getReferencedObjectClassName()).isEqualTo("user");
        assertThat(memberAttrs.get("project_id").isRequired()).isTrue();
        assertThat(memberAttrs.get("role_id").isRequired()).isTrue();
    }

    @Test
    public void testMultiplePoolConnections() throws SQLException {
        try (var conn1 = context.getConnection();
             var conn2 = context.getConnection()) {

            try (var s1 = conn1.getConnection().createStatement();
                 var s2 = conn2.getConnection().createStatement()) {
                try (var rs = s1.executeQuery("SELECT COUNT(*) FROM \"User\"")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
            }
        }
    }

    @Test
    public void testCloseAndReinit() {
        context.close();
        // After close, getConnectionPool() returns null (pool is destroyed)
        // We verify by checking that getConnection() throws IllegalStateException
        try {
            context.getConnection();
            fail("Should throw IllegalStateException after close");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).isEqualTo("Connection pool not initialized");
        }

        context.initializeConnectionPool();
        context.getConnection();  // Should work now

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
                .filter(table -> name.equalsIgnoreCase(table.getName()))
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
}
