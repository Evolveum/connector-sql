/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.sql.base.test.SqlSchemaAssertions;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for schema customization via manifest-based Groovy scripts.
 * Uses {@link ManifestBasedConnector} which loads scripts from {@code connector.manifest.json}
 * on the test classpath.
 */
@Test(singleThreaded = true)
public class SqlSchemaCustomizationIntegrationTest {

    private static final String URL = "jdbc:h2:mem:schemacust;DB_CLOSE_DELAY=-1";

    private static class TestSqlConnector extends ManifestBasedConnector {
        TestSqlConnector() {
            var config = new SqlConnectorConfiguration();
            config.setJdbcUrl(URL);
            config.setUsername("sa");
            config.setPassword(new GuardedString("".toCharArray()));
            config.setScanTables(true);
            config.setScanViews(true);
            TestSqlConnector.super.init(config);
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) { }
    }

    private void initTables() throws Exception {
        try (var c = DriverManager.getConnection(URL, "sa", "");
             var s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS app_user CASCADE");
            s.execute("DROP TABLE IF EXISTS app_group CASCADE");
            s.execute("""
                    CREATE TABLE app_user (
                        user_id INT PRIMARY KEY,
                        user_name VARCHAR(50) NOT NULL,
                        user_email VARCHAR(100),
                        user_created_at TIMESTAMP,
                        user_status VARCHAR(20))""");
            s.execute("""
                    CREATE TABLE app_group (
                        group_id INT PRIMARY KEY,
                        group_name VARCHAR(50) NOT NULL,
                        group_description VARCHAR(200))""");
            s.execute("INSERT INTO app_user VALUES (1, 'alice', 'alice@test.com', CURRENT_TIMESTAMP(), 'active')");
            s.execute("INSERT INTO app_user VALUES (2, 'bob', 'bob@test.com', CURRENT_TIMESTAMP(), 'active')");
            s.execute("INSERT INTO app_group VALUES (10, 'Admins', 'System administrators')");
            s.execute("INSERT INTO app_group VALUES (20, 'Users', 'Regular users')");
        }
    }

    private TestSqlConnector newConnector() throws Exception {
        initTables();
        return new TestSqlConnector();
    }

    // ── Schema object class names ──
    // Person maps to app_user with __UID__=user_id, __NAME__=user_name
    // Team maps to app_group with __UID__=group_id, __NAME__=group_name
    // SQL table names must NOT appear in schema

    @Test
    public void objectClassNamesCustomized() throws Exception {
        var conn = newConnector();
        try {
            var schema = conn.schema();
            List<String> names = schema.getObjectClassInfo().stream()
                    .map(ObjectClassInfo::getType).collect(Collectors.toList());
            assertThat(names).contains("Person", "Team");
            assertThat(names).doesNotContain("app_user", "app_group");
        } finally {
            conn.dispose();
        }
    }

    // ── Person: __UID__ → user_id, __NAME__ → user_name ──
    // Custom attribute: emailAddress (from user_email)
    // Custom attribute: loginCount (not on SQL table)

    @Test
    public void personUidAndNameMapping() throws Exception {
        var conn = newConnector();
        try {
            SqlSchemaAssertions.sqlAssert(conn.schema())
                    .objectClass("Person")
                    .hasUidColumn("user_id")
                    .hasNameColumn("user_name");
        } finally {
            conn.dispose();
        }
    }

    @Test
    public void emailAddressAttributeConnIdName() throws Exception {
        var conn = newConnector();
        try {
            SqlSchemaAssertions.sqlAssert(conn.schema())
                    .objectClass("Person")
                    .hasAttribute("emailAddress")
                    .nativeName("user_email");
        } finally {
            conn.dispose();
        }
    }

    @Test
    public void customLoginCountAttribute() throws Exception {
        var conn = newConnector();
        try {
            SqlSchemaAssertions.sqlAssert(conn.schema())
                    .objectClass("Person")
                    .hasAttribute("loginCount")
                    .nativeName("loginCount")
                    .type(String.class);
        } finally {
            conn.dispose();
        }
    }

    // ── Team: __UID__ → group_id, __NAME__ → group_name ──

    @Test
    public void teamUidAndNameMapping() throws Exception {
        var conn = newConnector();
        try {
            SqlSchemaAssertions.sqlAssert(conn.schema())
                    .objectClass("Team")
                    .hasUidColumn("group_id")
                    .hasNameColumn("group_name");
        } finally {
            conn.dispose();
        }
    }
}
