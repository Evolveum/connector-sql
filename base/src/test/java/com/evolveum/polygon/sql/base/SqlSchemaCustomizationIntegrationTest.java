/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.groovy.SqlHandlerBuilder;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

    private TestSqlConnector connector;

    private static class TestSqlConnector extends ManifestBasedConnector {
        TestSqlConnector() {
            var config = new SqlConnectorConfiguration();
            config.setJdbcUrl(URL);
            config.setUsername("sa");
            config.setPassword("");
            config.setAutoDiscoverSchema(true);
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
            s.execute("CREATE TABLE app_user ("
                    + "user_id INT PRIMARY KEY, "
                    + "user_name VARCHAR(50) NOT NULL, "
                    + "user_email VARCHAR(100), "
                    + "user_created_at TIMESTAMP, "
                    + "user_status VARCHAR(20))");
            s.execute("CREATE TABLE app_group ("
                    + "group_id INT PRIMARY KEY, "
                    + "group_name VARCHAR(50) NOT NULL, "
                    + "group_description VARCHAR(200))");
            s.execute("INSERT INTO app_user VALUES (1, 'alice', 'alice@test.com', CURRENT_TIMESTAMP(), 'active')");
            s.execute("INSERT INTO app_user VALUES (2, 'bob', 'bob@test.com', CURRENT_TIMESTAMP(), 'active')");
            s.execute("INSERT INTO app_group VALUES (10, 'Admins', 'System administrators')");
            s.execute("INSERT INTO app_group VALUES (20, 'Users', 'Regular users')");
        }
    }

    @Test
    public void personObjectClassHasCorrectUidNameMapping() throws Exception {
        initTables();
        connector = new TestSqlConnector();
        try {
            var schema = connector.schema();
            var personOci = schema.getObjectClassInfo().stream()
                    .filter(o -> "Person".equals(o.getType()))
                    .findFirst().orElseThrow(() -> new AssertionError("Person object class not found"));

            Map<String, AttributeInfo> attrs = personOci.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));

            var uidAttr = attrs.get(Uid.NAME);
            assertThat(uidAttr).isNotNull();
            assertThat(uidAttr.getNativeName()).isEqualTo("user_id");

            var nameAttr = attrs.get(Name.NAME);
            assertThat(nameAttr).isNotNull();
            assertThat(nameAttr.getNativeName()).isEqualTo("user_name");
        } finally {
            connector.dispose();
            connector = null;
        }
    }

    @Test
    public void teamObjectClassHasCustomNames() throws Exception {
        initTables();
        connector = new TestSqlConnector();
        try {
            var schema = connector.schema();
            var teamOci = schema.getObjectClassInfo().stream()
                    .filter(o -> "Team".equals(o.getType()))
                    .findFirst().orElseThrow(() -> new AssertionError("Team object class not found"));

            Map<String, AttributeInfo> attrs = teamOci.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));

            var uidAttr = attrs.get(Uid.NAME);
            assertThat(uidAttr).isNotNull();
            assertThat(uidAttr.getNativeName()).isEqualTo("group_id");

            var nameAttr = attrs.get(Name.NAME);
            assertThat(nameAttr).isNotNull();
            assertThat(nameAttr.getNativeName()).isEqualTo("group_name");
        } finally {
            connector.dispose();
            connector = null;
        }
    }

    @Test
    public void emailAddressAttributeConnIdName() throws Exception {
        initTables();
        connector = new TestSqlConnector();
        try {
            var schema = connector.schema();
            var personOci = schema.getObjectClassInfo().stream()
                    .filter(o -> "Person".equals(o.getType()))
                    .findFirst().orElseThrow(() -> new AssertionError("Person object class not found"));

            Map<String, AttributeInfo> attrs = personOci.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));

            // Check that emailAddress attribute exists (mapped from user_email)
            var emailAttr = attrs.get("emailAddress");
            var emailAttrOrig = attrs.get("user_email");

            // ConnId name must be exactly emailAddress, not user_email
            assertThat(emailAttr).isNotNull();
            assertThat(emailAttr.getNativeName()).isEqualTo("user_email");
            assertThat(emailAttrOrig).isNull();
        } finally {
            connector.dispose();
            connector = null;
        }
    }

    @Test
    public void customLoginCountAttribute() throws Exception {
        initTables();
        connector = new TestSqlConnector();
        try {
            var schema = connector.schema();
            var personOci = schema.getObjectClassInfo().stream()
                    .filter(o -> "Person".equals(o.getType()))
                    .findFirst().orElseThrow();

            Map<String, AttributeInfo> attrs = personOci.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));

            // loginCount attribute should exist (custom, not on the SQL table)
            var loginCountAttr = attrs.get("loginCount");
            assertThat(loginCountAttr).isNotNull();
            assertThat(loginCountAttr.getNativeName()).isEqualTo("loginCount");
            assertThat(loginCountAttr.getType()).isEqualTo(String.class);
        } finally {
            connector.dispose();
            connector = null;
        }
    }

    @Test
    public void sqlTableNamesDoNotAppearInSchema() throws Exception {
        initTables();
        connector = new TestSqlConnector();
        try {
            var schema = connector.schema();

            List<String> objectClassNames = schema.getObjectClassInfo().stream()
                    .map(ObjectClassInfo::getType)
                    .collect(Collectors.toList());

            assertThat(objectClassNames).doesNotContain("app_user", "app_group");
            assertThat(objectClassNames).contains("Person", "Team");
        } finally {
            connector.dispose();
            connector = null;
        }
    }

    @Test
    public void personAndTeamObjectClassesExist() throws Exception {
        initTables();
        connector = new TestSqlConnector();
        try {
            var schema = connector.schema();

            List<String> types = schema.getObjectClassInfo().stream()
                    .map(ObjectClassInfo::getType)
                    .collect(Collectors.toList());

            assertThat(types).contains("Person", "Team");
        } finally {
            connector.dispose();
            connector = null;
        }
    }
}