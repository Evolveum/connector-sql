/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.groovy.SqlGroovySchemaLoader;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerBuilder;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end (H2) test of the SQL development-mode export: the connector exposes {@code conndev_ObjectClass}
 * in its schema.
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

        @Override
        protected void initializeSchema(SqlGroovySchemaLoader loader) {
            // No scripts to load - schema is auto-discovered from DB tables
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        try (var c = DriverManager.getConnection(URL, "sa", ""); var s = c.createStatement()) {
            s.execute("DROP ALL OBJECTS");
            s.execute("CREATE TABLE app_user (id INT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(50) NOT NULL)");
            s.execute("CREATE TABLE app_group (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50))");
            s.execute("""
                    CREATE TABLE membership (id INT PRIMARY KEY AUTO_INCREMENT, user_id INT NOT NULL, \
                    CONSTRAINT fk_m_user FOREIGN KEY (user_id) REFERENCES app_user(id))""");
        }
    }

    private TestSqlConnector devConnector() {
        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(URL);
        config.setUsername("sa");
        config.setPassword(new GuardedString("".toCharArray()));
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
            assertThat(types).contains(
                    "conndev_ObjectClass", "conndev_Attribute", "conndev_connIdAttribute", "conndev_sql");
        } finally {
            connector.dispose();
        }
    }

}
