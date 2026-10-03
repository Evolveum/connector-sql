/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.test.PostgresDatabaseInitializer;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for WP 12165: the connector must not emit {@code ConnectorObjectReference}
 * attributes without a {@code roleInReference}. midPoint's {@code ConnIdSchemaParser} rejects such
 * attributes during resource schema processing ("Missing role in reference in ..."), which makes
 * connector initialization fail.
 *
 * <p>Runs against embedded PostgreSQL 16 (zonky) using the exact schema from the work package.
 */
@Test(singleThreaded = true)
public class SqlReferenceRolePostgresTest {

    static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    private PostgresDatabaseInitializer postgres;
    private SqlBaseContext context;

    @BeforeMethod
    public void setUp() throws Exception {
        postgres = PostgresDatabaseInitializer.create();

        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setScanTables(true);
        config.setScanViews(true);

        context = new SqlBaseContext(config);
        context.initializeConnectionPool();

        try (var conn = context.getConnection()) {
            executeSql(conn.getConnection(), "postgresql/reference-role/schema.sql");
        } catch (Exception e) {
            context.close();
            throw e;
        }
    }

    @AfterMethod
    public void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
        if (postgres != null) {
            postgres.close();
            postgres = null;
        }
    }

    @Test
    public void wpSchemaReferenceAttributesCarryRoles() throws Exception {
        var schema = buildConnIdSchema();
        assertMidPointAcceptableReferenceRoles(schema);
    }

    @Test
    public void junctionReferenceAttributesCarrySubjectRole() throws Exception {
        // The work package's three tables alone carry no junction (the accounts table has a
        // surrogate PK and a single FK not part of the PK). A link table connecting users and
        // accounts is the minimal trigger for the auto-detected reference attribute.
        executeRawSql(context.getConnection().getConnection(), """
                CREATE TABLE schema_b.user_account_link (
                    user_id INTEGER NOT NULL,
                    account_id BIGINT NOT NULL,

                    CONSTRAINT pk_user_account_link
                        PRIMARY KEY (user_id, account_id),

                    CONSTRAINT fk_user_account_link_user
                        FOREIGN KEY (user_id)
                        REFERENCES schema_b.users(user_id),

                    CONSTRAINT fk_user_account_link_account
                        FOREIGN KEY (account_id)
                        REFERENCES schema_b.accounts(account_id)
                );
                INSERT INTO schema_b.user_account_link (user_id, account_id)
                SELECT u.user_id, a.account_id
                FROM schema_b.users u
                JOIN schema_b.accounts a ON a.user_id = u.user_id;
                """);

        var schema = buildConnIdSchema();
        assertMidPointAcceptableReferenceRoles(schema);

        // Both sides of the detected junction expose a multi-valued reference; the referring
        // object class participates as the subject of the link.
        var usersAccounts = referenceAttribute(schema, "users", "accounts");
        assertThat(usersAccounts.getType()).isEqualTo(ConnectorObjectReference.class);
        assertThat(usersAccounts.isMultiValued()).isTrue();
        assertThat(usersAccounts.getReferencedObjectClassName()).isEqualTo("accounts");
        assertThat(usersAccounts.getRoleInReference())
                .isEqualTo(AttributeInfo.RoleInReference.SUBJECT.toString());

        var accountsUsers = referenceAttribute(schema, "accounts", "users");
        assertThat(accountsUsers.getRoleInReference())
                .isEqualTo(AttributeInfo.RoleInReference.SUBJECT.toString());
    }

    /**
     * Replicates midPoint's {@code ConnIdSchemaParser#determineParticipantRole}: every reference
     * attribute must carry a role of {@code __SUBJECT__} or {@code __OBJECT__}; a missing (or
     * unsupported) role makes midPoint fail connector initialization.
     */
    static void assertMidPointAcceptableReferenceRoles(Schema schema) {
        for (var oc : schema.getObjectClassInfo()) {
            for (var attr : oc.getAttributeInfo()) {
                if (!attr.isReference()) {
                    continue;
                }
                var role = attr.getRoleInReference();
                assertThat(role)
                        .describedAs(
                                "midPoint ConnIdSchemaParser fails connector initialization: "
                                        + "Missing role in reference in <%s> of object class '%s'",
                                attr, oc.getType())
                        .isIn(
                                AttributeInfo.RoleInReference.SUBJECT.toString(),
                                AttributeInfo.RoleInReference.OBJECT.toString());
            }
        }
    }

    private static AttributeInfo referenceAttribute(Schema schema, String objectClass, String attribute) {
        ObjectClassInfo oc = findObjectClass(schema, objectClass);
        return oc.getAttributeInfo().stream()
                .filter(a -> attribute.equalsIgnoreCase(a.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Reference attribute '%s' not found on object class '%s'; attributes: %s"
                                .formatted(attribute, objectClass,
                                        oc.getAttributeInfo().stream().map(AttributeInfo::getName).toList())));
    }

    private static ObjectClassInfo findObjectClass(Schema schema, String name) {
        return schema.getObjectClassInfo().stream()
                .filter(oc -> name.equalsIgnoreCase(oc.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Object class '%s' not found in schema; present: %s"
                                .formatted(name,
                                        schema.getObjectClassInfo().stream()
                                                .map(ObjectClassInfo::getType)
                                                .toList())));
    }

    private Schema buildConnIdSchema() throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        var translator = new SqlSchemaTranslator(tables);
        var builder = translator.translate(StubConnector.class, context);
        translator.applyRules();
        builder.applyStructuralRules();
        return builder.build().connIdSchema();
    }

    private static void executeSql(Connection conn, String resourcePath) throws Exception {
        try (var is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new Exception("Resource not found: " + resourcePath);
            }
            var sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (var stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }

    private void executeRawSql(Connection conn, String sql) throws Exception {
        try (var stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }
}
