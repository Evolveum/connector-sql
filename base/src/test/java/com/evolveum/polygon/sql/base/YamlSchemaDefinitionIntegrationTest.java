/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.groovy.SqlHandlerBuilder;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The declarative YAML schema front-end ({@code *.native.schema.yaml}, {@code sql:} block) doing
 * exactly what {@link SqlSchemaCustomizationIntegrationTest}'s Groovy {@code customize.groovy} does —
 * same two tables, same renames — proving the YAML path is a real alternative, not an inert one.
 */
@Test(singleThreaded = true)
public class YamlSchemaDefinitionIntegrationTest {

    private static final String URL = "jdbc:h2:mem:yamlschemacust;DB_CLOSE_DELAY=-1";

    private static class TestSqlConnector extends ManifestBasedConnector {
        TestSqlConnector() {
            super("/yaml-schema-e2e/connector.manifest");
            var config = new SqlConnectorConfiguration();
            config.setJdbcUrl(URL);
            config.setUsername("sa");
            config.setPassword(new GuardedString("".toCharArray()));
            config.setScanTables(true);
            config.setScanViews(true);
            TestSqlConnector.super.init(config);
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerBuilder builder) { }
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
                    user_email VARCHAR(100))""");
            s.execute("""
                    CREATE TABLE app_group (
                    group_id INT PRIMARY KEY,
                    group_name VARCHAR(50) NOT NULL)""");
        }
    }

    @Test
    public void personAndTeamObjectClassesReplaceRawTableNames() throws Exception {
        initTables();
        var connector = new TestSqlConnector();
        try {
            var schema = connector.schema();

            List<String> types = schema.getObjectClassInfo().stream()
                    .map(ObjectClassInfo::getType)
                    .collect(Collectors.toList());

            assertThat(types).contains("Person", "Team");
            assertThat(types).doesNotContain("app_user", "app_group");
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void personObjectClassHasUidNameAndCustomAttributes() throws Exception {
        initTables();
        var connector = new TestSqlConnector();
        try {
            var schema = connector.schema();
            var personOci = schema.getObjectClassInfo().stream()
                    .filter(o -> "Person".equals(o.getType()))
                    .findFirst().orElseThrow(() -> new AssertionError("Person object class not found"));

            Map<String, AttributeInfo> attrs = personOci.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));

            assertThat(attrs.get(Uid.NAME).getNativeName()).isEqualTo("user_id");
            assertThat(attrs.get(Name.NAME).getNativeName()).isEqualTo("user_name");
            assertThat(attrs.get("emailAddress")).isNotNull();
            assertThat(attrs.get("emailAddress").getNativeName()).isEqualTo("user_email");
            assertThat(attrs.get("user_email")).isNull();
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void teamObjectClassHasCustomUidAndNameMapping() throws Exception {
        initTables();
        var connector = new TestSqlConnector();
        try {
            var schema = connector.schema();
            var teamOci = schema.getObjectClassInfo().stream()
                    .filter(o -> "Team".equals(o.getType()))
                    .findFirst().orElseThrow(() -> new AssertionError("Team object class not found"));

            Map<String, AttributeInfo> attrs = teamOci.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));

            assertThat(attrs.get(Uid.NAME).getNativeName()).isEqualTo("group_id");
            assertThat(attrs.get(Name.NAME).getNativeName()).isEqualTo("group_name");
        } finally {
            connector.dispose();
        }
    }
}
