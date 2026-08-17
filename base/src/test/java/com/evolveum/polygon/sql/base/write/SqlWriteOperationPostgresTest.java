/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import com.evolveum.polygon.sql.base.test.PostgresDatabaseInitializer;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostgreSQL 16 coverage for SQL write operations and dialect-specific generated keys. */
@Test(singleThreaded = true)
public class SqlWriteOperationPostgresTest {

    private static final ObjectClass USER = new ObjectClass("app_user");
    private static final ObjectClass COMPOSITE_RECORD = new ObjectClass("composite_record");
    private static final ObjectClass ACCOUNT = new ObjectClass("account");
    private static final ObjectClass QUOTED_ACCOUNT = new ObjectClass("QuotedAccount");
    private static final ObjectClass PROJECT_MEMBERSHIP = new ObjectClass("projectmembership");

    private PostgresDatabaseInitializer postgres;
    private TestSqlConnector connector;

    private static class TestSqlConnector
            extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {

        TestSqlConnector() {
            super(false);
        }

        @Override
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) {
            // Schema is discovered from the test database.
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) {
            // Use built-in operation handlers.
        }
    }

    @BeforeClass
    public void setUp() throws Exception {
        postgres = PostgresDatabaseInitializer.create();
        var password = new GuardedStringAccessor();
        postgres.getPassword().access(password);
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), password.getClearString())) {
            executeSql(connection, "postgresql/basic/schema.sql");
            executeSql(connection, "postgresql/basic/data.sql");
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE composite_record (
                            partition_key INT NOT NULL,
                            subject_key INT NOT NULL,
                            role_name VARCHAR(64) NOT NULL,
                            PRIMARY KEY (partition_key, subject_key)
                        );
                        CREATE SCHEMA provisioning;
                        CREATE TABLE provisioning.account (
                            id BIGSERIAL PRIMARY KEY,
                            login VARCHAR(255) NOT NULL UNIQUE,
                            enabled BOOLEAN NOT NULL,
                            quota NUMERIC(10, 2)
                        );
                        CREATE SCHEMA "ProvisioningCase";
                        CREATE TABLE "ProvisioningCase"."QuotedAccount" (
                            "Id" BIGSERIAL PRIMARY KEY,
                            "Login" VARCHAR(255) NOT NULL UNIQUE,
                            "Enabled" BOOLEAN NOT NULL
                        );
                        """);
            }
        }

        var configuration = new SqlConnectorConfiguration();
        configuration.setJdbcUrl(postgres.getJdbcUrl());
        configuration.setUsername(postgres.getUsername());
        configuration.setPassword(postgres.getPassword());
        configuration.setPoolSize(5);
        configuration.setConnectionTimeout(10000);
        configuration.setScanTables(true);
        configuration.setScanViews(true);

        connector = new TestSqlConnector();
        connector.init(configuration);
        connector.schema();
    }

    @AfterClass
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
    public void testPostgresCreateUpdateDeleteConstraintsAndCompositeUid() {
        var createdAt = ZonedDateTime.now().withNano(0);
        var userUid = connector.create(USER, Set.of(
                AttributeBuilder.build(Name.NAME, "postgres.user"),
                AttributeBuilder.build("username", "postgres.user"),
                AttributeBuilder.build("email", "postgres.user@example.com"),
                AttributeBuilder.build("created_at", createdAt)), options());

        assertThat(userUid.getUidValue()).isNotBlank();
        assertThat(value(get(USER, userUid), "username")).isEqualTo("postgres.user");
        assertThat(value(get(USER, userUid), "created_at")).isNotNull();

        var email = AttributeDeltaBuilder.build("email", List.of("changed@example.com"));
        connector.updateDelta(USER, userUid, Set.of(email), options());
        assertThat(value(get(USER, userUid), "email")).isEqualTo("changed@example.com");

        assertThatThrownBy(() -> connector.create(USER, Set.of(
                AttributeBuilder.build(Name.NAME, "postgres.user"),
                AttributeBuilder.build("username", "postgres.user")), options()))
                .isInstanceOf(AlreadyExistsException.class);

        var beforeFailedForeignKeyInsert = search(PROJECT_MEMBERSHIP, null).size();
        assertThatThrownBy(() -> connector.create(PROJECT_MEMBERSHIP, Set.of(
                AttributeBuilder.build(Name.NAME, "invalid-membership"),
                AttributeBuilder.build("user_id", 99999),
                AttributeBuilder.build("project_id", 1),
                AttributeBuilder.build("role_id", 1)), options()))
                .isInstanceOf(InvalidAttributeValueException.class);
        assertThat(search(PROJECT_MEMBERSHIP, null)).hasSize(beforeFailedForeignKeyInsert);

        var membershipUid = connector.create(COMPOSITE_RECORD, Set.of(
                AttributeBuilder.build(Uid.NAME, "7.8"),
                AttributeBuilder.build(Name.NAME, "7.8"),
                AttributeBuilder.build("role_name", "member")), options());
        assertThat(membershipUid.getUidValue()).isEqualTo("7.8");
        connector.delete(COMPOSITE_RECORD, membershipUid, options());
        assertThat(search(COMPOSITE_RECORD, uidFilter(membershipUid))).isEmpty();

        var accountUid = connector.create(ACCOUNT, Set.of(
                AttributeBuilder.build(Name.NAME, "qualified.account"),
                AttributeBuilder.build("login", "qualified.account"),
                AttributeBuilder.build("enabled", true),
                AttributeBuilder.build("quota", new BigDecimal("125.50"))), options());
        var account = get(ACCOUNT, accountUid);
        assertThat(value(account, "enabled")).isEqualTo(true);
        assertThat(value(account, "quota")).isEqualTo(new BigDecimal("125.50"));

        connector.delete(ACCOUNT, accountUid, options());

        var quotedAccountUid = connector.create(QUOTED_ACCOUNT, Set.of(
                AttributeBuilder.build(Name.NAME, "quoted.account"),
                AttributeBuilder.build("Login", "quoted.account"),
                AttributeBuilder.build("Enabled", true)), options());
        var quotedAccount = get(QUOTED_ACCOUNT, quotedAccountUid);
        assertThat(value(quotedAccount, "Login")).isEqualTo("quoted.account");
        assertThat(value(quotedAccount, "Enabled")).isEqualTo(true);
        connector.delete(QUOTED_ACCOUNT, quotedAccountUid, options());

        connector.delete(USER, userUid, options());
        assertThat(search(USER, uidFilter(userUid))).isEmpty();
    }

    private OperationOptions options() {
        return new OperationOptions(Collections.emptyMap());
    }

    private ConnectorObject get(ObjectClass objectClass, Uid uid) {
        var result = search(objectClass, uidFilter(uid));
        assertThat(result).hasSize(1);
        return result.getFirst();
    }

    private List<ConnectorObject> search(ObjectClass objectClass, Filter filter) {
        var result = new ArrayList<ConnectorObject>();
        connector.executeQuery(objectClass, filter, result::add, options());
        return result;
    }

    private Filter uidFilter(Uid uid) {
        return FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, uid.getUidValue()));
    }

    private Object value(ConnectorObject object, String attributeName) {
        var values = object.getAttributeByName(attributeName).getValue();
        return values.isEmpty() ? null : values.getFirst();
    }

    private static void executeSql(Connection connection, String resourcePath) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute(readResource(resourcePath));
        }
    }

    private static String readResource(String path) throws IOException {
        var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        return new String(Objects.requireNonNull(stream, "Resource not found: " + path).readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
