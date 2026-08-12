/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.test.SqlIntegrationTestBase;
import com.evolveum.polygon.sql.base.test.SqlSchemaAssertions;
import org.identityconnectors.common.security.GuardedString;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for child table auto-detection from JDBC FK metadata.
 */
@Test(singleThreaded = true)
public class ChildTableAutoDetectionTest
        extends SqlIntegrationTestBase<ChildTableAutoDetectionTest.TestSqlConnector> {

    protected static class TestSqlConnector extends DefaultTestConnector {
        protected TestSqlConnector() { super(); }
    }

    @Override
    protected String[] resourceSchemaPaths() {
        return new String[]{"h2/child-tables/schema.sql", "h2/child-tables/data.sql"};
    }

    @Override
    protected SqlConnectorConfiguration buildConfiguration() {
        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(url);
        config.setUsername("sa");
        config.setPassword(new GuardedString("".toCharArray()));
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setScanTables(true);
        config.setScanViews(false);
        config.setDevelopmentMode(false);
        return config;
    }

    @Override
    protected void initConnector() {
        connector = new TestSqlConnector();
        connector.init(defaultConfig());
    }

    @Test
    public void testSchemaContainsParentTables() {
        assertThat(schemaNames()).contains("users", "groups");
    }

    @Test
    public void testChildTablesAreEmbeddedInSchema() {
        // user_emails (FK + 1 value col) → simple attribute (no OC)
        // user_profiles (FK = PK) → embedded OC
        // user_addresses (FK not in PK) → standalone OC
        // user_phones (FK + 2+ value cols) → embedded OC
        assertThat(schemaNames())
                .contains("user_profiles", "user_addresses", "user_phones")
                .doesNotContain("user_emails");
    }

    @Test
    public void testChildTablesAreMarkedEmbedded() {
        var schema = connector.schema();
        SqlSchemaAssertions.sqlAssert(schema).objectClass("user_profiles").isEmbedded();
        SqlSchemaAssertions.sqlAssert(schema).objectClass("user_addresses").isNotEmbedded();
        SqlSchemaAssertions.sqlAssert(schema).objectClass("user_phones").isEmbedded();
    }

    @Test
    public void testJunctionTableNotInSchema() {
        assertThat(schemaNames()).doesNotContain("user_group_membership");
    }

    @Test
    public void testParentHasEmbeddedAttributes() {
        SqlSchemaAssertions.sqlAssert(connector.schema())
                .objectClass("users")
                .hasAttributesInsensitive("user_profiles", "user_emails", "user_phones");
    }

    @Test
    public void testProfileIsSingleValued() {
        SqlSchemaAssertions.sqlAssert(connector.schema())
                .objectClass("users").hasAttributeInsensitive("user_profiles").isSingleValued();
    }

    @Test
    public void testEmailsMultiValued() {
        SqlSchemaAssertions.sqlAssert(connector.schema())
                .objectClass("users").hasAttributeInsensitive("user_emails").isMultiValued();
    }
}
