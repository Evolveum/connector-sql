/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
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
import java.util.ArrayList;
import java.util.Collections;
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

        var idCol = columns.get("ID");
        assertThat(idCol).isNotNull();
        assertThat(idCol.isPrimaryKey()).isTrue();
        assertThat(idCol.isNullable()).isFalse();
        assertThat(idCol.isAutoIncrement()).isTrue();

        assertThat(columns.containsKey("USERNAME")).isTrue();
        assertThat(columns.get("USERNAME").isNullable()).isFalse();
        assertThat(columns.get("USERNAME").isUnique()).isTrue();

        assertThat(columns.containsKey("EMAIL")).isTrue();
        assertThat(columns.get("EMAIL").isNullable()).isTrue();
        assertThat(columns.get("EMAIL").isUnique()).isTrue();

        assertThat(columns.containsKey("CREATED_AT")).isTrue();
        assertThat(columns.get("CREATED_AT").isNullable()).isTrue();
    }

    @Test
    public void testGroupTableSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "group"));
        assertThat(columns.size()).isEqualTo(3);
        assertThat(columns.get("ID").isNullable()).isFalse();
        assertThat(columns.get("ID").isPrimaryKey()).isTrue();
    }

    @Test
    public void testRoleTableSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "role"));
        assertThat(columns.size()).isEqualTo(3);
        assertThat(columns.get("ID").isPrimaryKey()).isTrue();
        assertThat(columns.get("NAME").isNullable()).isFalse();
    }

    @Test
    public void testProjectTableSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "project"));
        assertThat(columns.size()).isEqualTo(4);
        assertThat(columns.get("ID").isPrimaryKey()).isTrue();
        assertThat(columns.get("NAME").isNullable()).isFalse();
    }

    @Test
    public void testUserAddressSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "USERADDRESS"));
        assertThat(columns.size()).isEqualTo(6);
        assertThat(columns.get("ID").isPrimaryKey()).isTrue();

        var userIdCol = columns.get("USER_ID");
        assertThat(userIdCol).isNotNull();
        assertThat(userIdCol.isNullable()).withFailMessage("UserAddress.user_id should be NOT NULL - UserAddress cannot exist without a User").isFalse();

        assertColumnType(columns.get("STREET"), "VARCHAR");
        assertColumnType(columns.get("CITY"), "VARCHAR");
        assertColumnType(columns.get("COUNTRY"), "VARCHAR");
    }

    @Test
    public void testProjectMembershipSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        Map<String, SqlColumnMeta> columns = toColumnMap(table(tables, "projectmembership"));
        assertThat(columns.size()).isEqualTo(5);
        assertThat(columns.get("ID").isPrimaryKey()).isTrue();

        assertThat(columns.get("USER_ID").isNullable()).isFalse();
        assertThat(columns.get("PROJECT_ID").isNullable()).isFalse();
        assertThat(columns.get("ROLE_ID").isNullable()).isFalse();
        assertThat(columns.get("JOINED_AT").isNullable()).isTrue();
    }

    @Test
    public void testConnIdSchemaTranslation() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        var translator = new SqlSchemaTranslator(tables);
        var builder = translator.translate(StubConnector.class, context);
        translator.applyRules();
        builder.applyStructuralRules();
        var connIdSchema = builder.build().connIdSchema();
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
        assertThat(userAttrs.get(Uid.NAME).getNativeName()).isEqualTo("ID");
        assertThat(userAttrs.get("EMAIL").isRequired()).withFailMessage("User.email should NOT be required").isFalse();

        // Verify ProjectMembership: FK columns are references, still required
        var membership = objClasses.get("projectmembership");
        assertThat(membership).isNotNull();
        Map<String, AttributeInfo> memberAttrs = membership.getAttributeInfo().stream()
                .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));
        assertThat(memberAttrs.size()).isEqualTo(6);  // 5 columns + __NAME__ auto-added by ConnId

        assertThat(memberAttrs.get("USER_ID").isRequired()).isTrue();
        //assertThat(memberAttrs.get("user_id").getReferencedObjectClassName()).isEqualTo("user");
        assertThat(memberAttrs.get("PROJECT_ID").isRequired()).isTrue();
        assertThat(memberAttrs.get("ROLE_ID").isRequired()).isTrue();
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

    @Test
    public void testTargetedDiscoveryByTableRef() throws Exception {
        var detector = new SqlSchemaDetector(context);
        var tableRef = new SqlSchemaDetector.TableRef("", "User");
        List<SqlTableInfo> tables = detector.discover(Collections.singletonList(tableRef));

        assertThat(tables).hasSize(1);
        assertThat(tables).first().extracting(SqlTableInfo::getName).isEqualTo("User");
        assertThat(tables.getFirst().getColumns()).isNotEmpty();
        assertThat(toColumnMap(tables.getFirst()).containsKey("ID")).isTrue();
    }

    @Test
    public void testTargetedDiscoveryMultipleTables() throws Exception {
        var detector = new SqlSchemaDetector(context);
        var tableRefs = List.of(
                new SqlSchemaDetector.TableRef("", "User"),
                new SqlSchemaDetector.TableRef("", "Group")
        );
        List<SqlTableInfo> tables = detector.discover(tableRefs);

        assertThat(tables).hasSize(2);
        assertThat(tables.stream().map(SqlTableInfo::getName))
                .containsExactlyInAnyOrder("User", "Group");
    }

    @Test
    public void testTargetedDiscoveryNonExistentTable() throws Exception {
        var detector = new SqlSchemaDetector(context);
        var tableRef = new SqlSchemaDetector.TableRef("", "nonexistent_table");
        List<SqlTableInfo> tables = detector.discover(Collections.singletonList(tableRef));

        assertThat(tables).isEmpty();
    }

    @Test
    public void testTargetedDiscoveryEmptyList() throws Exception {
        var detector = new SqlSchemaDetector(context);
        List<SqlTableInfo> tables = detector.discover(Collections.emptyList());
        assertThat(tables).isEmpty();
    }

    @Test
    public void testTargetedDiscoveryNullList() throws Exception {
        var detector = new SqlSchemaDetector(context);
        List<SqlTableInfo> tables = detector.discover(null);
        assertThat(tables).isEmpty();
    }

    @Test
    public void testTargetedDiscoveryWithSqlSchemaTranslator() throws Exception {
        var detector = new SqlSchemaDetector(context);
        var builder = new SqlSchemaBuilderImpl(
                StubConnector.class, context);

        // Define an object class with SQL table mapping
        builder.objectClass("User").sql().table("User");

        var tableRefs = builder.tableRefs();
        assertThat(tableRefs).hasSize(1);

        var tables = detector.discover(new ArrayList<>(tableRefs));
        var translator = new SqlSchemaTranslator(builder, tables);
        translator.translate(StubConnector.class, context);
        translator.applyRules();
        builder.applyStructuralRules();
        var schema = builder.build();

        assertThat(schema).isNotNull();
        assertThat(schema.connIdSchema().getObjectClassInfo()).isNotEmpty();
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
