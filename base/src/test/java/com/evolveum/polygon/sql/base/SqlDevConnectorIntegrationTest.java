/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import com.evolveum.polygon.sql.base.test.SqlIntegrationTestBase;
import com.evolveum.polygon.sql.base.test.SqlSchemaAssertions;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end (H2) test of the SQL development-mode export: the connector exposes
 * {@code conndev_ObjectClass} in its schema.
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
                CREATE TABLE app_user (id INT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(50) NOT NULL);
                CREATE TABLE app_group (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50));
                CREATE TABLE membership (id INT PRIMARY KEY AUTO_INCREMENT, user_id INT NOT NULL,
                    CONSTRAINT fk_m_user FOREIGN KEY (user_id) REFERENCES app_user(id));
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
                "conndev_objectclass", "conndev_attribute", "conndev_connidattribute", "conndev_sql");
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
