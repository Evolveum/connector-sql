/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.sql.base.test.SqlSchemaAssertions;
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
 * A Groovy schema that explicitly declares {@code sql { type INT }} on the {@code __UID__}
 * attribute must still expose the UID as a ConnId {@link String} — both in the schema
 * ({@code AttributeInfo} type) and in the values returned by reads.
 */
@Test(singleThreaded = true)
public class SqlUidStringTypeIntegrationTest {

    // PostgreSQL compatibility mode with lowercase identifiers — like the reported
    // PostgreSQL setup, the detected table is "public"."user_view" with lowercase columns.
    private static final String URL =
            "jdbc:h2:mem:uidstring;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
    private static final ObjectClass USER_VIEW = new ObjectClass("user_view");

    private static class TestSqlConnector extends ManifestBasedConnector {
        TestSqlConnector() {
            super("/manifests/uid-int/connector.manifest");
            var config = new SqlConnectorConfiguration();
            config.setJdbcUrl(URL);
            config.setUsername("sa");
            config.setPassword(new GuardedString("".toCharArray()));
            config.setScanTables(true);
            config.setScanViews(true);
            // H2's PostgreSQL mode exposes pg_* catalog tables — scan only the test table.
            config.setScanTableFilter("user_view");
            TestSqlConnector.super.init(config);
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) { }
    }

    private TestSqlConnector newConnector() throws Exception {
        try (var c = DriverManager.getConnection(URL, "sa", "");
             var s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS user_view CASCADE");
            s.execute("""
                    CREATE TABLE user_view (
                        id INT PRIMARY KEY,
                        username VARCHAR(255),
                        city VARCHAR(255),
                        country VARCHAR(255),
                        created_at TIMESTAMP(6),
                        email VARCHAR(255),
                        street VARCHAR(255))""");
            s.execute("""
                    INSERT INTO user_view VALUES
                    (1, 'alice', 'Prague', 'CZ', CURRENT_TIMESTAMP(), 'alice@test.com', 'Main St')""");
        }
        return new TestSqlConnector();
    }

    private List<ConnectorObject> query(TestSqlConnector conn, Filter filter) throws Exception {
        var results = new ArrayList<ConnectorObject>();
        conn.executeQuery(USER_VIEW, filter, results::add, new OperationOptions(Collections.emptyMap()));
        return results;
    }

    @Test
    public void uidAttributeIsStringTypedInSchema() throws Exception {
        var conn = newConnector();
        try {
            SqlSchemaAssertions.sqlAssert(conn.schema())
                    .objectClass("user_view")
                    .hasAttribute(Uid.NAME)
                    .type(String.class);
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
            var nameAttribute = object.getAttributes().stream()
                    .filter(a -> Name.NAME.equals(a.getName()))
                    .findFirst()
                    .orElseThrow();
            assertThat(nameAttribute.getValue().getFirst()).isEqualTo("alice");
        } finally {
            conn.dispose();
        }
    }
}
