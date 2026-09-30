/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.sql.base.test.SqlSchemaAssertions;
import com.querydsl.core.types.Constant;
import com.querydsl.core.types.dsl.BooleanOperation;
import com.querydsl.core.types.dsl.NumberPath;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A manifest that declares the {@code __UID__} attribute with {@code sql.type: SERIAL} — the type
 * name produced by schema discovery/export for PostgreSQL serial primary keys — must map the
 * column to its integer wire type. The UID stays a ConnId {@link String}, but filter values are
 * converted to the protocol (integer) type when the query predicate is built, so the parameter
 * is bound as an integer rather than a varchar (PostgreSQL rejects {@code integer = varchar}).
 */
@Test(singleThreaded = true)
public class SqlUidSerialTypeIntegrationTest {

    // PostgreSQL compatibility mode with lowercase identifiers — like the reported
    // PostgreSQL setup, the detected table is "public"."app_user" with lowercase columns.
    private static final String URL =
            "jdbc:h2:mem:uidserial;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
    private static final ObjectClass APP_USER = new ObjectClass("app_user");

    private static class TestSqlConnector extends ManifestBasedConnector {
        TestSqlConnector() {
            super("/manifests/uid-serial/connector.manifest");
            var config = new SqlConnectorConfiguration();
            config.setJdbcUrl(URL);
            config.setUsername("sa");
            config.setPassword(new GuardedString("".toCharArray()));
            config.setScanTables(true);
            config.setScanViews(true);
            // H2's PostgreSQL mode exposes pg_* catalog tables — scan only the test table.
            config.setScanTableFilter("app_user");
            TestSqlConnector.super.init(config);
        }

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) { }
    }

    private TestSqlConnector newConnector() throws Exception {
        try (var c = DriverManager.getConnection(URL, "sa", "");
             var s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS app_user CASCADE");
            s.execute("""
                    CREATE TABLE app_user (
                        id SERIAL PRIMARY KEY,
                        username VARCHAR(255) NOT NULL,
                        email VARCHAR(255),
                        birthdate DATE,
                        created_at TIMESTAMP)""");
            s.execute("""
                    INSERT INTO app_user (username, email, birthdate, created_at)
                    VALUES ('alice', 'alice@test.com', DATE '1990-01-01', CURRENT_TIMESTAMP())""");
        }
        return new TestSqlConnector();
    }

    private List<ConnectorObject> query(TestSqlConnector conn, Filter filter) throws Exception {
        var results = new ArrayList<ConnectorObject>();
        conn.executeQuery(APP_USER, filter, results::add, new OperationOptions(Collections.emptyMap()));
        return results;
    }

    @Test
    public void uidAttributeIsStringTypedInSchema() throws Exception {
        var conn = newConnector();
        try {
            SqlSchemaAssertions.sqlAssert(conn.schema())
                    .objectClass("app_user")
                    .hasAttribute(Uid.NAME)
                    .type(String.class);
        } finally {
            conn.dispose();
        }
    }

    @Test
    public void uidFilterPredicateBindsIntegerProtocolType() throws Exception {
        var conn = newConnector();
        try {
            var objectClass = conn.context().schema().objectClass(APP_USER);
            var uidMapping = objectClass.attributeFromConnIdName(Uid.NAME).sql();
            var singleColumn = (SqlAttributeMapping.SingleColumn) uidMapping;
            var tablePath = objectClass.sql().pathAlias("o");

            assertThat(singleColumn.dslPath(tablePath))
                    .as("UID query path must be a number path (integer column), not a string path")
                    .isInstanceOf(NumberPath.class);

            var predicate = uidMapping.sqlFilter().eq(tablePath, "1");
            var constant = (Constant<?>) ((BooleanOperation) predicate).getArgs().get(1);
            assertThat(constant.getConstant())
                    .as("String UID value must be converted to the column's integer wire type")
                    .isInstanceOf(Integer.class)
                    .isEqualTo(1);
        } finally {
            conn.dispose();
        }
    }

    @Test
    public void uidValueIsStringWhenRead() throws Exception {
        var conn = newConnector();
        try {
            var filter = FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, "1"));
            var objects = query(conn, filter);
            assertThat(objects).hasSize(1);
            var object = objects.getFirst();
            assertThat(object.getUid().getUidValue()).isEqualTo("1");
            var uidAttribute = object.getAttributes().stream()
                    .filter(a -> Uid.NAME.equals(a.getName()))
                    .findFirst()
                    .orElseThrow();
            assertThat(uidAttribute.getValue().getFirst())
                    .as("__UID__ value should be converted from INT to String")
                    .isInstanceOf(String.class)
                    .isEqualTo("1");
        } finally {
            conn.dispose();
        }
    }
}
