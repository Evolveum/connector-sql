/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.test;

import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.dev.SqlDevelopmentMode;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to validate Oracle database schema + initialization.
 * Both modes connect to the same external Oracle instance
 * (localhost:1521/FREEPDB1).
 * <p>
 * Two test modes are provided via inner classes:
 * {@link OracleScriptSchemaTest} and {@link OracleAutoDiscoveryTest}.
 */
public abstract class OracleConnectorIntegrationTest {

    protected OracleDatabaseInitializer oracle;
    protected TestOracleConnector connector;

    /** Returns true when the connector uses a Groovy schema script instead of auto-discovery. */
    protected abstract boolean useScriptSchema();

    protected static class TestOracleConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        private final boolean loadGroovySchema;

        protected TestOracleConnector(boolean loadGroovySchema) {
            super(false);
            this.loadGroovySchema = loadGroovySchema;
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) {
        }

        @Override
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) {
            if (loadGroovySchema) {
                loader.loadFromResource("/oracle/basic/oracle.groovy");
            }
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        oracle = OracleDatabaseInitializer.create();
        oracle.init();

        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl("jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
        config.setUsername("oracle");
        config.setPassword(new GuardedString("oracle123".toCharArray()));
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setScanTables(!useScriptSchema());
        config.setScanViews(!useScriptSchema());
        config.setDevelopmentMode(true);

        connector = new TestOracleConnector(useScriptSchema());
        connector.init(config);
    }

    @AfterMethod
    public void tearDown() {
        if (connector != null) { connector.dispose(); connector = null; }
        if (oracle != null) { oracle.close(); oracle = null; }
    }

    protected OperationOptions opts() {
        return new OperationOptions(Collections.emptyMap());
    }

    // ── shared test methods (executed for both modes) ──

    @Test
    public void testSchemaContainsAllTables() throws Exception {
        var schema = connector.schema();
        assertThat(schema.getObjectClassInfo()).isNotEmpty();

        List<String> names = schema.getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType)
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        assertThat(names).contains(
                "orgchart_type_ref",
                "orgchart_node",
                "orgchart_label",
                "dir_status_ref",
                "dir_archetype_ref",
                "dir_staff_origin_ref",
                "dir_institution_ref",
                "dir_auth_domain",
                "dir_account",
                "dir_service",
                "dir_membership",
                "dir_xf_entitlement",
                "dir_job_watermark"
        );
    }

    @Test
    public void testSearchOrgchartNode() throws Exception {
        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(new ObjectClass("orgchart_node"), null, results::add, null);

        assertThat(results).isNotEmpty();
        for (ConnectorObject o : results) {
            assertThat(o.getUid()).isNotNull();
            assertThat(o.getName()).isNotNull();
        }
    }

    @Test
    public void testSearchDirAccount() throws Exception {
        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(new ObjectClass("dir_account"), null, results::add, opts());

        assertThat(results).hasSize(4);
        for (ConnectorObject o : results) {
            assertThat(o.getUid().getValue()).isNotNull();
        }
    }

    @Test
    public void testSearchDirMembership() throws Exception {
        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(new ObjectClass("dir_membership"), null, results::add, opts());

        assertThat(results).hasSize(2);
    }

    @Test
    public void testSearchAllStandaloneObjectClassesWork() throws Exception {
        // orgchart_label is embedded in orgchart_node and therefore has no standalone search handler.
        for (String name : List.of(
                "orgchart_type_ref",
                "orgchart_node",
                "dir_status_ref",
                "dir_account",
                "dir_service",
                "dir_membership")) {
            List<ConnectorObject> r = new ArrayList<>();
            connector.executeQuery(new ObjectClass(name), null, r::add, opts());
            assertThat(r).withFailMessage("No results for " + name).isNotEmpty();
        }
    }

    @Test
    public void testDevelopmentTableMetadataExport() throws Exception {
        assertThat(connector.schema().getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType))
                .contains(SqlDevelopmentMode.TABLE_OC_NAME);

        List<ConnectorObject> tables = new ArrayList<>();
        connector.executeQuery(
                new ObjectClass(SqlDevelopmentMode.TABLE_OC_NAME), null, tables::add, opts());

        var dirAccount = tables.stream()
                .filter(table -> "DIR_ACCOUNT".equalsIgnoreCase(table.getName().getNameValue()))
                .findFirst()
                .orElseThrow();
        var content = (String) AttributeUtil.getSingleValue(
                dirAccount.getAttributeByName(SqlDevelopmentMode.TABLE_CONTENT_ATTRIBUTE));
        var definition = (String) AttributeUtil.getSingleValue(
                dirAccount.getAttributeByName(SqlDevelopmentMode.DEFINITION_ATTRIBUTE));

        assertThat(AttributeUtil.getSingleValue(
                dirAccount.getAttributeByName(SqlDevelopmentMode.SCHEMA_ATTRIBUTE)))
                .isEqualTo("ORACLE");
        assertThat(AttributeUtil.getSingleValue(
                dirAccount.getAttributeByName(SqlDevelopmentMode.TABLE_TYPE_ATTRIBUTE)))
                .isEqualTo("TABLE");
        assertThat(definition)
                .contains("CREATE TABLE \"ORACLE\".\"DIR_ACCOUNT\"")
                .contains("\"ACCOUNT_ID\" VARCHAR2(8)")
                .contains("DEFAULT SYSTIMESTAMP")
                .contains("CONSTRAINT \"FK_ACCT_STATUS\" FOREIGN KEY");
        assertThat(content)
                .contains("\"name\" : \"ACCOUNT_ID\"")
                .contains("\"primaryKey\" : true")
                .contains("CREATE TABLE \\\"ORACLE\\\".\\\"DIR_ACCOUNT\\\"")
                .contains("\"referencedTable\" : \"DIR_STATUS_REF\"")
                .contains("\"referencedColumn\" : \"STATUS_CODE\"")
                .contains("\"foreignKeyName\" : \"FK_ACCT_STATUS\"");
    }

    // ── concrete test classes ──

    /**
     * Oracle tests using a Groovy schema script (autoDiscoverSchema = false).
     */
    @Test(singleThreaded = true)
    public static class OracleScriptSchemaTest extends OracleConnectorIntegrationTest {

        @Override
        protected boolean useScriptSchema() {
            return true;
        }

        @Test
        public void testConnIdTypesCorrectlyMapped() throws Exception {
            var schema = connector.schema();

            var orgchartTypeRef = schema.getObjectClassInfo().stream()
                    .filter(o -> "orgchart_type_ref".equals(o.getType()))
                    .findFirst().orElseThrow();

            Map<String, AttributeInfo> orgchartAttrs = orgchartTypeRef.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, a -> a));

            assertThat(orgchartAttrs).containsKey(Uid.NAME);
            assertThat(orgchartAttrs.get(Uid.NAME).getNativeName()).isEqualTo("TYPE_REF_ID");

            assertThat(orgchartAttrs).containsKey(Name.NAME);
            assertThat(orgchartAttrs.get(Name.NAME).getNativeName()).isEqualTo("DISPLAY_NAME");

            var dirAccount = schema.getObjectClassInfo().stream()
                    .filter(o -> "dir_account".equals(o.getType()))
                    .findFirst().orElseThrow();

            Map<String, AttributeInfo> dirAccountAttrs = dirAccount.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, a -> a));

            assertThat(dirAccountAttrs).containsKey(Uid.NAME);
            assertThat(dirAccountAttrs.get(Uid.NAME).getNativeName()).isEqualTo("ACCOUNT_ID");

            assertThat(dirAccountAttrs).containsKey(Name.NAME);
            assertThat(dirAccountAttrs.get(Name.NAME).getNativeName()).isEqualTo("FAMILY_NAME");

            var dirMembership = schema.getObjectClassInfo().stream()
                    .filter(o -> "dir_membership".equals(o.getType()))
                    .findFirst().orElseThrow();

            Map<String, AttributeInfo> membershipAttrs = dirMembership.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, a -> a));

            assertThat(membershipAttrs).containsKey(Uid.NAME);
            assertThat(membershipAttrs.get(Uid.NAME).getNativeName()).isEqualTo("ACCOUNT_ID");

            assertThat(membershipAttrs).containsKey(Name.NAME);
            assertThat(membershipAttrs.get(Name.NAME).getNativeName()).isEqualTo("SERVICE_ID");
        }

        @Test
        public void testNumericTypesCorrectlyMapped() throws Exception {
            var schema = connector.schema();

            // UIDs are always mapped to STRING regardless of SQL type
            var orgchartTypeRef = schema.getObjectClassInfo().stream()
                    .filter(o -> "orgchart_type_ref".equals(o.getType()))
                    .findFirst().orElseThrow();

            Map<String, AttributeInfo> orgchartAttrs = orgchartTypeRef.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, a -> a));
            assertThat(orgchartAttrs.get(Uid.NAME).getType()).isEqualTo(String.class);

            // orgchart_node: all non-UID attributes are VARCHAR or plain NUMBER fields
            var orgchartNode = schema.getObjectClassInfo().stream()
                    .filter(o -> "orgchart_node".equals(o.getType()))
                    .findFirst().orElseThrow();

            Map<String, AttributeInfo> nodeAttrs = orgchartNode.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, a -> a));
            assertThat(nodeAttrs.get(Uid.NAME).getType()).isEqualTo(String.class);
            assertThat(nodeAttrs.get("PARENT_UNIT_ID").getType()).isEqualTo(BigDecimal.class);
            assertThat(nodeAttrs.get("TYPE_REF_ID").getType()).isEqualTo(BigDecimal.class);
            assertThat(nodeAttrs.get("HIERARCHY_LEVEL").getType()).isEqualTo(BigDecimal.class);
            assertThat(nodeAttrs.get("DISPLAY_ORDER").getType()).isEqualTo(BigDecimal.class);

            // dir_membership: account_id is UID → STRING, service_id is NAME → STRING
            var dirMembership = schema.getObjectClassInfo().stream()
                    .filter(o -> "dir_membership".equals(o.getType()))
                    .findFirst().orElseThrow();

            Map<String, AttributeInfo> membershipAttrs = dirMembership.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, a -> a));
            assertThat(membershipAttrs.get(Uid.NAME).getType()).isEqualTo(String.class);
            assertThat(membershipAttrs.get(Name.NAME).getType()).isEqualTo(String.class);

            // Verify all non-connId VARCHAR attributes are String
            var dirAccount = schema.getObjectClassInfo().stream()
                    .filter(o -> "dir_account".equals(o.getType()))
                    .findFirst().orElseThrow();

            Map<String, AttributeInfo> acctAttrs = dirAccount.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, a -> a));
            assertThat(acctAttrs.get(Uid.NAME).getType()).isEqualTo(String.class);
            assertThat(acctAttrs.get("EMAIL_ADDRESS").getType()).isEqualTo(String.class);
            assertThat(acctAttrs.get("STATUS_CODE").getType()).isEqualTo(String.class);
        }
    }

    /**
     * Oracle tests using runtime schema auto-discovery (autoDiscoverSchema = true).
     * No Groovy schema script is loaded.
     */
    @Test(singleThreaded = true)
    public static class OracleAutoDiscoveryTest extends OracleConnectorIntegrationTest {

        @Override
        protected boolean useScriptSchema() {
            return false;
        }
    }
}
