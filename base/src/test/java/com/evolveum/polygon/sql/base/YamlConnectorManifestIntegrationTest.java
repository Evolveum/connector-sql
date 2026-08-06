/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ManifestBasedConnector} loading a {@code connector.manifest.yaml} instead of the default
 * {@code connector.manifest.json} — same {@code /schema/customize.groovy} script as
 * {@link SqlSchemaCustomizationIntegrationTest}, only the manifest format (and its resource base
 * name) differs.
 */
@Test(singleThreaded = true)
public class YamlConnectorManifestIntegrationTest {

    private static final String URL = "jdbc:h2:mem:yamlmanifest;DB_CLOSE_DELAY=-1";

    private static class TestSqlConnector extends ManifestBasedConnector {
        TestSqlConnector() {
            super("/manifests/yaml/connector.manifest");
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

    @Test
    public void personObjectClassHasCorrectUidNameMapping() throws Exception {
        try (var c = DriverManager.getConnection(URL, "sa", "");
             var s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS app_user CASCADE");
            s.execute("""
                    CREATE TABLE app_user (
                    user_id INT PRIMARY KEY,
                    user_name VARCHAR(50) NOT NULL,
                    user_email VARCHAR(100))""");
        }

        var connector = new TestSqlConnector();
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
        } finally {
            connector.dispose();
        }
    }
}
