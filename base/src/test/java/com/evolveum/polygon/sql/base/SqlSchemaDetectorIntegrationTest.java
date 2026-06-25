/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.connection.SqlConnection;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlSchema;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.test.H2DatabaseInitializer;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        SqlSchema schema = context.schema();
        assertThat(schema).isNotNull();
        assertThat(schema.getTables().size()).withFailMessage("Should discover exactly 6 tables").isEqualTo(6);
    }

    @Test
    public void testUserTableSchema() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        SqlTableInfo userTable = context.schema().getTable("user");
        assertThat(userTable).isNotNull();
        Map<String, SqlColumnMeta> columns = toColumnMap(userTable);

        SqlColumnMeta idCol = columns.get("id");
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
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(context.schema().getTable("group"));
        assertThat(columns.size()).isEqualTo(3);
        assertThat(columns.get("id").isNullable()).isFalse();
        assertThat(columns.get("id").isPrimaryKey()).isTrue();
    }

    @Test
    public void testRoleTableSchema() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(context.schema().getTable("role"));
        assertThat(columns.size()).isEqualTo(3);
        assertThat(columns.get("id").isPrimaryKey()).isTrue();
        assertThat(columns.get("name").isNullable()).isFalse();
    }

    @Test
    public void testProjectTableSchema() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(context.schema().getTable("project"));
        assertThat(columns.size()).isEqualTo(4);
        assertThat(columns.get("id").isPrimaryKey()).isTrue();
        assertThat(columns.get("name").isNullable()).isFalse();
    }

    @Test
    public void testUserAddressSchema() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(context.schema().getTable("useraddress"));
        assertThat(columns.size()).isEqualTo(6);
        assertThat(columns.get("id").isPrimaryKey()).isTrue();

        SqlColumnMeta userIdCol = columns.get("user_id");
        assertThat(userIdCol).isNotNull();
        assertThat(userIdCol.isNullable()).withFailMessage("UserAddress.user_id should be NOT NULL - UserAddress cannot exist without a User").isFalse();

        assertColumnType(columns.get("street"), "VARCHAR");
        assertColumnType(columns.get("city"), "VARCHAR");
        assertColumnType(columns.get("country"), "VARCHAR");
    }

    @Test
    public void testProjectMembershipSchema() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(context.schema().getTable("projectmembership"));
        assertThat(columns.size()).isEqualTo(5);
        assertThat(columns.get("id").isPrimaryKey()).isTrue();

        assertThat(columns.get("user_id").isNullable()).isFalse();
        assertThat(columns.get("project_id").isNullable()).isFalse();
        assertThat(columns.get("role_id").isNullable()).isFalse();
        assertThat(columns.get("joined_at").isNullable()).isTrue();
    }

    @Test
    public void testConnIdSchemaTranslation() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Schema connIdSchema = context.schema().connIdSchema();
        assertThat(connIdSchema).isNotNull();

        Map<String, ObjectClassInfo> objClasses = connIdSchema.getObjectClassInfo().stream()
                .collect(Collectors.toMap(
                        info -> info.getType().toLowerCase(),
                        Function.identity()));

        for (String name : List.of("user", "group", "role", "project", "useraddress", "projectmembership")) {
            assertThat(objClasses.containsKey(name)).withFailMessage("Should contain '" + name + "' object class").isTrue();
        }

        // Verify User attributes
        ObjectClassInfo userClass = objClasses.get("user");
        assertThat(userClass).isNotNull();
        Map<String, AttributeInfo> userAttrs = userClass.getAttributeInfo().stream()
                .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));
        assertThat(userAttrs.size()).isEqualTo(5);  // 4 columns + __NAME__ auto-added by ConnId

        assertThat(userAttrs.get("id").isRequired()).withFailMessage("User.id should be required").isTrue();
        assertThat(userAttrs.get("email").isRequired()).withFailMessage("User.email should NOT be required").isFalse();

        // Verify ProjectMembership
        ObjectClassInfo membership = objClasses.get("projectmembership");
        assertThat(membership).isNotNull();
        Map<String, AttributeInfo> memberAttrs = membership.getAttributeInfo().stream()
                .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));
        assertThat(memberAttrs.size()).isEqualTo(6);  // 5 columns + __NAME__ auto-added by ConnId

        assertThat(memberAttrs.get("user_id").isRequired()).isTrue();
        assertThat(memberAttrs.get("project_id").isRequired()).isTrue();
        assertThat(memberAttrs.get("role_id").isRequired()).isTrue();
    }

    @Test
    public void testMultiplePoolConnections() throws SQLException {
        try (SqlConnection conn1 = context.getConnection();
             SqlConnection conn2 = context.getConnection()) {

            try (Statement s1 = conn1.getConnection().createStatement();
                 Statement s2 = conn2.getConnection().createStatement()) {
                try (ResultSet rs = s1.executeQuery("SELECT COUNT(*) FROM \"User\"")) {
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

    private void assertColumnType(SqlColumnMeta col, String... allowedTypes) {
        String typeName = col.getTypeName().toUpperCase();
        for (String t : allowedTypes) {
            if (typeName.equals(t) || typeName.contains(t)) return;
        }
        fail("Column '" + col.getName() + "' expected " + List.of(allowedTypes) + " but got " + typeName);
    }

    private Map<String, SqlColumnMeta> toColumnMap(SqlTableInfo table) {
        return table.getColumns().stream()
                .collect(Collectors.toMap(SqlColumnMeta::getName, Function.identity(), (a, b) -> a));
    }

    @Test
    public void testDebugTables() throws Exception {
        // Debug which tables are discovered
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();
        assertThat(context.schema().getTables()).withFailMessage("Should be 6").hasSize(6);
    }
}