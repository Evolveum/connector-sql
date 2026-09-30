/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.sql.base.test.PostgresDatabaseInitializer;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exact reproduction of the reported PostgreSQL failure: a manifest declaring the {@code __UID__}
 * attribute with {@code sql.type: SERIAL} (the type name produced by schema discovery/export for
 * serial primary keys). Without the integer type mapping, the UID filter binds a varchar
 * parameter against an {@code integer} column and PostgreSQL fails with
 * {@code operator does not exist: integer = character varying}.
 */
@Test(singleThreaded = true)
public class SqlUidSerialPostgresTest {

    private static final ObjectClass APP_USER = new ObjectClass("app_user");

    private PostgresDatabaseInitializer postgres;
    private TestSqlConnector connector;

    private static class TestSqlConnector extends ManifestBasedConnector {
        TestSqlConnector() {
            super("/manifests/uid-serial/connector.manifest");
        }

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) { }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        postgres = PostgresDatabaseInitializer.create();

        var password = new GuardedStringAccessor();
        postgres.getPassword().access(password);
        try (var conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), password.getClearString());
             var stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE app_user (
                        id SERIAL PRIMARY KEY,
                        username VARCHAR(255) NOT NULL,
                        email VARCHAR(255),
                        birthdate DATE,
                        created_at TIMESTAMP)""");
            stmt.execute("""
                    INSERT INTO app_user (username, email, birthdate, created_at)
                    VALUES ('alice', 'alice@test.com', DATE '1990-01-01', CURRENT_TIMESTAMP)""");
        }

        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setScanTables(true);
        config.setScanViews(true);
        connector = new TestSqlConnector();
        connector.init(config);
    }

    @AfterMethod
    public void tearDown() {
        if (connector != null) {
            connector.dispose();
            connector = null;
        }
        if (postgres != null) {
            postgres.close();
            postgres = null;
        }
    }

    @Test
    public void resolveBySerialUidBindsIntegerParameter() throws Exception {
        var filter = FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, "1"));
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(APP_USER, filter, results::add,
                new OperationOptions(Collections.emptyMap()));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUid().getUidValue()).isEqualTo("1");
        var uidAttribute = results.getFirst().getAttributes().stream()
                .filter(a -> Uid.NAME.equals(a.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(uidAttribute.getValue().getFirst())
                .as("__UID__ value should be converted from INT to String")
                .isInstanceOf(String.class)
                .isEqualTo("1");
    }

    @Test
    public void searchAllRowsReturnsSerialUidAsString() throws Exception {
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(APP_USER, null, results::add,
                new OperationOptions(Collections.emptyMap()));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUid().getUidValue()).isEqualTo("1");
        assertThat(getAttr(results.getFirst(), Name.NAME)).isEqualTo("alice");
    }

    private static Object getAttr(ConnectorObject object, String name) {
        var attr = object.getAttributeByName(name);
        return attr == null || attr.getValue().isEmpty() ? null : attr.getValue().getFirst();
    }
}
