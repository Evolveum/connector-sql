/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.dev.SqlDevelopmentMode;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import com.evolveum.polygon.sql.base.test.SqlIntegrationTestBase;
import com.evolveum.polygon.sql.base.test.SqlSchemaAssertions;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end (H2) tests of the SQL development-mode schema and raw table metadata exports.
 */
@Test(singleThreaded = true)
public class SqlDevConnectorIntegrationTest
        extends SqlIntegrationTestBase<SqlDevConnectorIntegrationTest.TestSqlConnector> {

    protected static class TestSqlConnector extends DefaultTestConnector {
        protected TestSqlConnector() { super(); }
    }

    @Override
    protected String schemaSql() {
        return """
                DROP ALL OBJECTS;
                CREATE TABLE app_user (id INT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(50) DEFAULT 'anonymous' NOT NULL);
                COMMENT ON TABLE app_user IS 'Application users';
                COMMENT ON COLUMN app_user.username IS 'Application login name';
                CREATE TABLE app_group (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50));
                CREATE TABLE membership (id INT PRIMARY KEY AUTO_INCREMENT, user_id INT NOT NULL,
                    CONSTRAINT fk_m_user FOREIGN KEY (user_id) REFERENCES app_user(id));
                CREATE VIEW app_user_view AS SELECT id, username FROM app_user;
                CREATE SCHEMA tenant_a;
                CREATE SCHEMA tenant_b;
                CREATE TABLE tenant_a.shared_entry (id INT PRIMARY KEY, entry_value VARCHAR(50));
                CREATE TABLE tenant_b.shared_entry (id INT PRIMARY KEY, entry_value VARCHAR(50));
                """;
    }

    @Override
    protected SqlConnectorConfiguration buildConfiguration() {
        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(url);
        config.setUsername("sa");
        config.setPassword(new GuardedString("".toCharArray()));
        config.setDevelopmentMode(true);
        return config;
    }

    @Override
    protected void initConnector() {
        connector = new TestSqlConnector();
        connector.init(defaultConfig());
    }

    @Test
    public void exposesConnDevObjectClassInSchema() {
        assertThat(schemaNames()).contains(
                "conndev_objectclass", "conndev_attribute", "conndev_connidattribute", "conndev_sql",
                "conndev_sqltable");
    }

    @Test
    public void exportsDetectedTableMetadata() throws Exception {
        var tables = search(SqlDevelopmentMode.TABLE_OC_NAME, null);

        var membership = tables.stream()
                .filter(table -> table.getName().getNameValue().equalsIgnoreCase("membership"))
                .findFirst()
                .orElseThrow();
        var content = (String) getAttr(membership, SqlDevelopmentMode.TABLE_CONTENT_ATTRIBUTE);

        assertThat(getAttr(membership, SqlDevelopmentMode.SCHEMA_ATTRIBUTE)).isEqualTo("PUBLIC");
        assertThat(content)
                .contains("\"columns\"")
                .contains("\"primaryKey\" : true")
                .contains("\"referencedTable\" : \"APP_USER\"")
                .contains("\"foreignKeyName\" : \"FK_M_USER\"");
    }

    @Test
    public void exportsTableAndColumnDescriptionsAndDefaults() throws Exception {
        var appUser = tableNamed(search(SqlDevelopmentMode.TABLE_OC_NAME, null), "app_user");
        var content = (String) getAttr(appUser, SqlDevelopmentMode.TABLE_CONTENT_ATTRIBUTE);

        assertThat(getAttr(appUser, SqlDevelopmentMode.REMARKS_ATTRIBUTE)).isEqualTo("Application users");
        assertThat(content)
                .contains("\"remarks\" : \"Application users\"")
                .contains("\"defaultValue\" : \"'anonymous'\"")
                .contains("\"remarks\" : \"Application login name\"");
    }

    @Test
    public void doesNotExposeTableMetadataOutsideDevelopmentMode() {
        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(url);
        config.setUsername("sa");
        config.setPassword(new GuardedString("".toCharArray()));
        config.setDevelopmentMode(false);

        var nonDevConnector = new TestSqlConnector();
        nonDevConnector.init(config);
        try {
            assertThat(nonDevConnector.schema().getObjectClassInfo().stream()
                    .map(ObjectClassInfo::getType))
                    .doesNotContain(SqlDevelopmentMode.TABLE_OC_NAME);
        } finally {
            nonDevConnector.dispose();
        }
    }

    @Test
    public void filtersTableMetadataByUidNameAndSchema() throws Exception {
        var appUser = tableNamed(search(SqlDevelopmentMode.TABLE_OC_NAME, null), "app_user");

        assertThat(search(SqlDevelopmentMode.TABLE_OC_NAME, FilterBuilder.equalTo(
                AttributeBuilder.build(Uid.NAME, appUser.getUid().getUidValue()))))
                .extracting(object -> object.getUid().getUidValue())
                .containsExactly(appUser.getUid().getUidValue());
        assertThat(search(SqlDevelopmentMode.TABLE_OC_NAME, FilterBuilder.equalTo(
                AttributeBuilder.build(Name.NAME, appUser.getName().getNameValue()))))
                .extracting(object -> object.getName().getNameValue())
                .containsExactly(appUser.getName().getNameValue());
        assertThat(search(SqlDevelopmentMode.TABLE_OC_NAME, FilterBuilder.equalTo(
                AttributeBuilder.build(SqlDevelopmentMode.SCHEMA_ATTRIBUTE, "PUBLIC"))))
                .isNotEmpty()
                .allSatisfy(object -> assertThat(getAttr(object, SqlDevelopmentMode.SCHEMA_ATTRIBUTE))
                        .isEqualTo("PUBLIC"));
    }

    @Test
    public void keepsSameNamedTablesFromDifferentSchemas() throws Exception {
        var sharedTables = search(SqlDevelopmentMode.TABLE_OC_NAME, null).stream()
                .filter(table -> table.getName().getNameValue().equalsIgnoreCase("shared_entry"))
                .toList();

        assertThat(sharedTables)
                .hasSize(2)
                .extracting(table -> getAttr(table, SqlDevelopmentMode.SCHEMA_ATTRIBUTE))
                .containsExactlyInAnyOrder("TENANT_A", "TENANT_B");
        assertThat(sharedTables)
                .extracting(table -> table.getUid().getUidValue())
                .doesNotHaveDuplicates();
    }

    @Test
    public void exportsViewMetadata() throws Exception {
        var view = tableNamed(search(SqlDevelopmentMode.TABLE_OC_NAME, null), "app_user_view");

        assertThat(getAttr(view, SqlDevelopmentMode.TABLE_TYPE_ATTRIBUTE)).isEqualTo("VIEW");
        assertThat((String) getAttr(view, SqlDevelopmentMode.TABLE_CONTENT_ATTRIBUTE))
                .contains("\"tableType\" : \"VIEW\"")
                .contains("\"name\" : \"USERNAME\"");
    }

    private static ConnectorObject tableNamed(List<ConnectorObject> tables, String name) {
        return tables.stream()
                .filter(table -> table.getName().getNameValue().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow();
    }

    // ─── Scan-disabled with Groovy-defined object classes ───

    protected static class TestSqlConnectorWithScanDisabled
            extends DefaultTestConnector {
        protected TestSqlConnectorWithScanDisabled() { super(); }

        @Override
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) {
            loader.loadFromResource("/test/objectClass/User.groovy");
            loader.loadFromResource("/test/objectClass/Group.groovy");
        }
    }

    private TestSqlConnectorWithScanDisabled initScanDisabledConnector() {
        var conn = new TestSqlConnectorWithScanDisabled();
        conn.init(noScanConfig());
        return conn;
    }

    @Test
    public void scanDisabledWithTableDefinitionsPerformsTargetedScanning() {
        var conn = initScanDisabledConnector();
        try {
            assertThat(conn.schema().getObjectClassInfo().stream()
                    .map(ObjectClassInfo::getType).toList())
                    .contains("User", "Group");
        } finally {
            conn.dispose();
        }
    }

    @Test
    public void scanDisabledWithTableDefinitionsHasUidColumn() {
        var conn = initScanDisabledConnector();
        try {
            SqlSchemaAssertions.sqlAssert(conn.schema())
                    .objectClass("User").hasUidColumn("id");
        } finally {
            conn.dispose();
        }
    }

    @Test
    public void scanDisabledWithTableDefinitionsHasColumnAttributes() {
        var conn = initScanDisabledConnector();
        try {
            SqlSchemaAssertions.sqlAssert(conn.schema())
                    .objectClass("User")
                    .hasAttributes(Uid.NAME, "username");
        } finally {
            conn.dispose();
        }
    }
}
