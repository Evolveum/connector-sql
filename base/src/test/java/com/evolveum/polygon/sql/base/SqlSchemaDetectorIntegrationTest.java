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
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.testng.Assert.*;

/**
 * Integration tests for SQL schema detection and translation using H2 embedded database.
 */
@Test(singleThreaded = true)
public class SqlSchemaDetectorIntegrationTest {

    private SqlConnectorConfiguration configuration;
    private SqlBaseContext context;

    @BeforeMethod
    public void setUp() throws Exception {
        String dbUrl = "jdbc:h2:mem:" + UUID.randomUUID().toString().replace("-", "_")
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL";

        configuration = new SqlConnectorConfiguration();
        configuration.setJdbcUrl(dbUrl);
        configuration.setUsername("sa");
        configuration.setPassword("");
        configuration.setPoolSize(5);
        configuration.setConnectionTimeout(30000);
        configuration.setIdleTimeout(600000);
        configuration.setValidateConnectionOnBorrow(true);
        configuration.setAutoDiscoverSchema(false);

        context = new SqlBaseContext(configuration);
        context.initializeConnectionPool();
        createTablesAndData();
    }

    @AfterMethod
    public void tearDown() {
        if (context != null) {
            context.close();
        }
        context = null;
    }

    private void createTablesAndData() throws SQLException {
        try (SqlConnection conn = context.getConnection()) {
            Connection jdbc = conn.getConnection();
            try (Statement stmt = jdbc.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS ProjectMembership CASCADE");
                stmt.execute("DROP TABLE IF EXISTS UserAddress CASCADE");
                stmt.execute("DROP TABLE IF EXISTS Project CASCADE");
                stmt.execute("DROP TABLE IF EXISTS \"Group\" CASCADE");
                stmt.execute("DROP TABLE IF EXISTS \"Role\" CASCADE");
                stmt.execute("DROP TABLE IF EXISTS \"User\" CASCADE");

                stmt.execute("""
                        CREATE TABLE "User" (\
                        id INT PRIMARY KEY AUTO_INCREMENT, \
                        username VARCHAR(255) NOT NULL UNIQUE, \
                        email VARCHAR(255) UNIQUE, \
                        created_at TIMESTAMP)""");
                stmt.execute("""
                        CREATE TABLE "Group" (\
                        id INT PRIMARY KEY AUTO_INCREMENT, \
                        name VARCHAR(255) NOT NULL, \
                        description VARCHAR(1024))""");
                stmt.execute("""
                        CREATE TABLE "Role" (\
                        id INT PRIMARY KEY AUTO_INCREMENT, \
                        name VARCHAR(255) NOT NULL, \
                        description VARCHAR(1024))""");
                stmt.execute("""
                        CREATE TABLE Project (\
                        id INT PRIMARY KEY AUTO_INCREMENT, \
                        name VARCHAR(255) NOT NULL, \
                        description VARCHAR(1024), \
                        created_at TIMESTAMP)""");
                stmt.execute("""
                        CREATE TABLE UserAddress (\
                        id INT PRIMARY KEY AUTO_INCREMENT, \
                        user_id INT NOT NULL, \
                        street VARCHAR(255), \
                        city VARCHAR(255), \
                        country VARCHAR(255), \
                        primary_flag BOOLEAN)""");
                stmt.execute("""
                        CREATE TABLE ProjectMembership (\
                        id INT PRIMARY KEY AUTO_INCREMENT, \
                        user_id INT NOT NULL, \
                        project_id INT NOT NULL, \
                        role_id INT NOT NULL, \
                        joined_at TIMESTAMP)""");

                stmt.execute("""
                        ALTER TABLE UserAddress ADD CONSTRAINT fk_user_address_user \
                        FOREIGN KEY (user_id) REFERENCES "User"(id) ON DELETE CASCADE""");
                stmt.execute("""
                        ALTER TABLE ProjectMembership ADD CONSTRAINT fk_membership_user \
                        FOREIGN KEY (user_id) REFERENCES "User"(id)""");
                stmt.execute("""
                        ALTER TABLE ProjectMembership ADD CONSTRAINT fk_membership_project \
                        FOREIGN KEY (project_id) REFERENCES Project(id)""");
                stmt.execute("""
                        ALTER TABLE ProjectMembership ADD CONSTRAINT fk_membership_role \
                        FOREIGN KEY (role_id) REFERENCES "Role"(id)""");

                stmt.execute("""
                        INSERT INTO "User" (username, email, created_at) VALUES \
                        ('john.doe', 'john@example.com', CURRENT_TIMESTAMP()), \
                        ('jane.smith', 'jane@example.com', CURRENT_TIMESTAMP())""");
                stmt.execute("""
                        INSERT INTO "Group" (name, description) VALUES \
                        ('Developers', 'Software developers'), \
                        ('Admins', 'System administrators')""");
                stmt.execute("""
                        INSERT INTO "Role" (name, description) VALUES \
                        ('Owner', 'Project owner'), \
                        ('Member', 'Regular member'), \
                        ('Reviewer', 'Code reviewer')""");
                stmt.execute("""
                        INSERT INTO Project (name, description, created_at) VALUES \
                        ('Alpha', 'First project', CURRENT_TIMESTAMP()), \
                        ('Beta', 'Second project', CURRENT_TIMESTAMP())""");
                stmt.execute("""
                        INSERT INTO UserAddress (user_id, street, city, country, primary_flag) VALUES \
                        (1, '123 Main St', 'New York', 'US', true), \
                        (1, '456 Oak Ave', 'Boston', 'US', false), \
                        (2, '789 Elm St', 'San Francisco', 'US', true)""");
                stmt.execute("""
                        INSERT INTO ProjectMembership (user_id, project_id, role_id, joined_at) VALUES \
                        (1, 1, 1, CURRENT_TIMESTAMP()), \
                        (1, 2, 2, CURRENT_TIMESTAMP()), \
                        (2, 1, 3, CURRENT_TIMESTAMP())""");
            }
        }
    }

    @Test
    public void testConnectionPoolInitialization() {
        assertNotNull(context.getConnectionPool());
        assertFalse(context.getConnectionPool().isClosed());
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
        assertNotNull(schema);
        assertEquals(schema.getTables().size(), 6, "Should discover exactly 6 tables");
    }

    @Test
    public void testUserTableSchema() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        SqlTableInfo userTable = context.schema().getTable("user");
        assertNotNull(userTable);
        Map<String, SqlColumnMeta> columns = toColumnMap(userTable);

        SqlColumnMeta idCol = columns.get("id");
        assertNotNull(idCol);
        assertTrue(idCol.isPrimaryKey());
        assertFalse(idCol.isNullable());
        assertTrue(idCol.isAutoIncrement());

        assertTrue(columns.containsKey("username"));
        assertFalse(columns.get("username").isNullable());
        assertTrue(columns.get("username").isUnique());

        assertTrue(columns.containsKey("email"));
        assertTrue(columns.get("email").isNullable());
        assertTrue(columns.get("email").isUnique());

        assertTrue(columns.containsKey("created_at"));
        assertTrue(columns.get("created_at").isNullable());
    }

    @Test
    public void testGroupTableSchema() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(context.schema().getTable("group"));
        assertEquals(columns.size(), 3);
        assertFalse(columns.get("id").isNullable());
        assertTrue(columns.get("id").isPrimaryKey());
    }

    @Test
    public void testRoleTableSchema() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(context.schema().getTable("role"));
        assertEquals(columns.size(), 3);
        assertTrue(columns.get("id").isPrimaryKey());
        assertFalse(columns.get("name").isNullable());
    }

    @Test
    public void testProjectTableSchema() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(context.schema().getTable("project"));
        assertEquals(columns.size(), 4);
        assertTrue(columns.get("id").isPrimaryKey());
        assertFalse(columns.get("name").isNullable());
    }

    @Test
    public void testUserAddressSchema() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(context.schema().getTable("useraddress"));
        assertEquals(columns.size(), 6);
        assertTrue(columns.get("id").isPrimaryKey());

        SqlColumnMeta userIdCol = columns.get("user_id");
        assertNotNull(userIdCol);
        assertFalse(userIdCol.isNullable(),
                "UserAddress.user_id should be NOT NULL - UserAddress cannot exist without a User");

        assertColumnType(columns.get("street"), "VARCHAR");
        assertColumnType(columns.get("city"), "VARCHAR");
        assertColumnType(columns.get("country"), "VARCHAR");
    }

    @Test
    public void testProjectMembershipSchema() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(context.schema().getTable("projectmembership"));
        assertEquals(columns.size(), 5);
        assertTrue(columns.get("id").isPrimaryKey());

        assertFalse(columns.get("user_id").isNullable());
        assertFalse(columns.get("project_id").isNullable());
        assertFalse(columns.get("role_id").isNullable());
        assertTrue(columns.get("joined_at").isNullable());
    }

    @Test
    public void testConnIdSchemaTranslation() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        Schema connIdSchema = context.schema().connIdSchema();
        assertNotNull(connIdSchema);

        Map<String, ObjectClassInfo> objClasses = connIdSchema.getObjectClassInfo().stream()
                .collect(Collectors.toMap(
                        info -> info.getType().toLowerCase(),
                        Function.identity()));

        for (String name : List.of("user", "group", "role", "project", "useraddress", "projectmembership")) {
            assertTrue(objClasses.containsKey(name), "Should contain '" + name + "' object class");
        }

        // Verify User attributes
        ObjectClassInfo userClass = objClasses.get("user");
        assertNotNull(userClass);
        Map<String, AttributeInfo> userAttrs = userClass.getAttributeInfo().stream()
                .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));
        assertEquals(userAttrs.size(), 5);  // 4 columns + __NAME__ auto-added by ConnId

        assertTrue(userAttrs.get("id").isRequired(), "User.id should be required");
        assertFalse(userAttrs.get("email").isRequired(), "User.email should NOT be required");

        // Verify ProjectMembership
        ObjectClassInfo membership = objClasses.get("projectmembership");
        assertNotNull(membership);
        Map<String, AttributeInfo> memberAttrs = membership.getAttributeInfo().stream()
                .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));
        assertEquals(memberAttrs.size(), 6);  // 5 columns + __NAME__ auto-added by ConnId

        assertTrue(memberAttrs.get("user_id").isRequired());
        assertTrue(memberAttrs.get("project_id").isRequired());
        assertTrue(memberAttrs.get("role_id").isRequired());
    }

    @Test
    public void testMultiplePoolConnections() throws SQLException {
        try (SqlConnection conn1 = context.getConnection();
             SqlConnection conn2 = context.getConnection()) {

            try (Statement s1 = conn1.getConnection().createStatement();
                 Statement s2 = conn2.getConnection().createStatement()) {
                try (ResultSet rs = s1.executeQuery("SELECT COUNT(*) FROM \"User\"")) {
                    assertTrue(rs.next());
                    assertEquals(rs.getInt(1), 2);
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
            assertEquals(e.getMessage(), "Connection pool not initialized");
        }

        context.initializeConnectionPool();
        context.getConnection();  // Should work now

        context.close();
        try {
            context.getConnection();
            fail("Should throw IllegalStateException after close");
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Connection pool not initialized");
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
        System.out.println("=== SCHEMA DEBUG ===");
        for (SqlTableInfo t : context.schema().getTables()) {
            System.out.println("DISCOVERED: " + t.getName() + " cols=" + t.getColumns().size());
            for (SqlColumnMeta c : t.getColumns()) {
                System.out.println("  col: " + c.getName() + " type=" + c.getTypeName() + " pk=" + c.isPrimaryKey() + " nullable=" + c.isNullable());
            }
        }
        System.out.println("==================");
        assertEquals(context.schema().getTables().size(), 6, "Should be 6");
    }
}
