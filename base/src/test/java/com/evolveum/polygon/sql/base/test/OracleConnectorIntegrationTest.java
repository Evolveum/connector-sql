/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.test;

import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilder;
import com.evolveum.polygon.sql.base.groovy.SqlGroovySchemaLoader;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerBuilder;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to validate Oracle database schema + initialization.
 * Connects to external Oracle instance (localhost:1521/FREEPDB1, user: oracle, password: oracle123).
 * On pre-init, drops all oracle tables; on post-init, loads schema + data.
 * Then runs connector operations against the seeded tables.
 */
@Test(singleThreaded = true)

public class OracleConnectorIntegrationTest {

    private OracleDatabaseInitializer oracle;
    private TestOracleConnector connector;

    private static class TestOracleConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        TestOracleConnector() { super(false); }
        @Override protected void initializeObjectClassHandler(SqlHandlerBuilder builder) {}

        @Override protected void initializeSchema(SqlSchemaBuilder builder) {
            builder.objectClass("ORGCHART_TYPE_REF")
                    .attribute("display_name").connId().name(Name.NAME);
            builder.objectClass("ORGCHART_LABEL")
                    .attribute("label_text").connId().name(Name.NAME);
        }
        @Override protected void initializeSchema(SqlGroovySchemaLoader loader) {
            // No scripts to load - schema is auto-discovered from DB tables
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
        config.setValidateConnectionOnBorrow(true);
        config.setAutoDiscoverSchema(true);
        config.setDevelopmentMode(true);

        connector = new TestOracleConnector();
        connector.init(config);
    }

    @AfterMethod
    public void tearDown() {
        if (connector != null) { connector.dispose(); connector = null; }
        if (oracle != null) { oracle.close(); oracle = null; }
    }

    private OperationOptions opts() {
        return new OperationOptions(Collections.emptyMap());
    }

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
    public void testSearchAllObjectClassesWork() throws Exception {
        for (String name : List.of(
                "orgchart_type_ref",
                "orgchart_node",
                "orgchart_label",
                "dir_status_ref",
                "dir_account",
                "dir_service",
                "dir_membership")) {
            List<ConnectorObject> r = new ArrayList<>();
            connector.executeQuery(new ObjectClass(name), null, r::add, opts());
            assertThat(r).withFailMessage("No results for " + name).isNotEmpty();
        }
    }
}