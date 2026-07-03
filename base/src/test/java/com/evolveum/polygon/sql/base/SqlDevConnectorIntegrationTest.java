/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.groovy.SqlHandlerBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end (H2) test of the SQL development-mode export: the connector exposes {@code conndev_ObjectClass}
 * in its schema and its inherited {@code executeQuery} dispatch returns one object per table, with foreign
 * keys expressed as references on the attributes.
 */
@Test(singleThreaded = true)
public class SqlDevConnectorIntegrationTest {

    private static final String URL = "jdbc:h2:mem:devtest;DB_CLOSE_DELAY=-1";

    /** Minimal concrete connector: no per-table handlers, just the inherited dispatch + dev export. */
    private static class TestSqlConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        TestSqlConnector() {
            super(false);
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerBuilder builder) {
            // no application object-class handlers needed for this test
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        try (var c = DriverManager.getConnection(URL, "sa", ""); var s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
            s.execute("CREATE TABLE app_user (id INT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(50) NOT NULL)");
            s.execute("CREATE TABLE app_group (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50))");
            s.execute("CREATE TABLE membership (id INT PRIMARY KEY AUTO_INCREMENT, user_id INT NOT NULL, "
                    + "CONSTRAINT fk_m_user FOREIGN KEY (user_id) REFERENCES app_user(id))");
        }
    }

    private TestSqlConnector devConnector() {
        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(URL);
        config.setUsername("sa");
        config.setPassword("");
        config.setDevelopmentMode(true);
        var connector = new TestSqlConnector();
        connector.init(config);
        return connector;
    }

    @Test
    public void exposesConnDevObjectClassInSchema() {
        var connector = devConnector();
        try {
            var types = connector.schema().getObjectClassInfo().stream()
                    .map(ObjectClassInfo::getType).collect(Collectors.toSet());
            assertThat(types).contains("conndev_ObjectClass", "conndev_Attribute");
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void searchesConnDevObjectClassThroughConnector() {
        var connector = devConnector();
        try {
            var results = new ArrayList<ConnectorObject>();
            connector.executeQuery(new ObjectClass("conndev_ObjectClass"), null, results::add, null);

            // one conndev_ObjectClass per table
            var names = results.stream()
                    .map(o -> o.getName().getNameValue().toLowerCase()).collect(Collectors.toSet());
            assertThat(names).contains("app_user", "app_group", "membership");

            // the FK on membership.user_id came through the whole chain as a reference on the attribute
            var membership = results.stream()
                    .filter(o -> o.getName().getNameValue().equalsIgnoreCase("membership"))
                    .findFirst().orElseThrow();
            var userId = attribute(membership, "user_id");
            assertThat(string(userId, "referencedObjectClass")).isEqualTo("app_user");
            assertThat(string(userId, "referencedAttribute")).isEqualTo("id");
            assertThat(string(userId, "reference")).isNotBlank();
        } finally {
            connector.dispose();
        }
    }

    private static EmbeddedObject attribute(ConnectorObject object, String name) {
        List<Object> attributes = object.getAttributeByName("attributes").getValue();
        return attributes.stream().map(EmbeddedObject.class::cast)
                .filter(e -> name.equals(string(e, "name")))
                .findFirst().orElseThrow(() -> new AssertionError("No attribute named " + name));
    }

    private static String string(EmbeddedObject object, String name) {
        var attribute = AttributeUtil.find(name, object.getAttributes());
        return attribute == null ? null : AttributeUtil.getStringValue(attribute);
    }
}
