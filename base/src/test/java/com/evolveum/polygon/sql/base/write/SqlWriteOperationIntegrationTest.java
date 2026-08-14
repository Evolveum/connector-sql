/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.conndev.spi.ObjectCreateOperation;
import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end H2 tests for the built-in SQL create, update, and delete handlers. */
@Test(singleThreaded = true)
public class SqlWriteOperationIntegrationTest {

    private static final ObjectClass USER = new ObjectClass("app_user");
    private static final ObjectClass EXTERNAL_ACCOUNT = new ObjectClass("external_account");
    private static final ObjectClass MEMBERSHIP = new ObjectClass("membership");
    private static final ObjectClass GENERATED_MEMBERSHIP = new ObjectClass("generated_membership");
    private static final ObjectClass SYNC_RECORD = new ObjectClass("sync_record");
    private static final ObjectClass USER_VIEW = new ObjectClass("app_user_view");

    private String jdbcUrl;
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
            // Built-in handlers are registered after explicit handlers.
        }
    }

    private static class CustomCreateConnector extends TestSqlConnector {

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) {
            ObjectCreateOperation custom = (attributes, options) -> new ConnectorObjectBuilder()
                    .setObjectClass(USER)
                    .setUid("custom-uid")
                    .setName("custom-name")
                    .build();
            builder.register(USER, ObjectCreateOperation.class, custom);
        }
    }

    private static class GroovyCustomHandlerConnector extends TestSqlConnector {

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) {
            builder.loadFromString("""
                    import com.evolveum.polygon.conndev.spi.ObjectCreateOperation
                    import com.evolveum.polygon.conndev.spi.ObjectDeleteOperation
                    import com.evolveum.polygon.conndev.spi.ObjectUpdateOperation
                    import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder
                    import org.identityconnectors.framework.common.objects.ObjectClass

                    def customCreateHandler = { attributes, options ->
                        new ConnectorObjectBuilder()
                            .setObjectClass(new ObjectClass("app_user"))
                            .setUid("groovy-uid")
                            .setName("groovy-name")
                            .build()
                    } as ObjectCreateOperation
                    def customUpdateHandler = { uid, modifications, options -> modifications
                    } as ObjectUpdateOperation
                    def customDeleteHandler = { uid, options ->
                    } as ObjectDeleteOperation

                    objectClass("APP_USER") {
                        create customCreateHandler
                        update customUpdateHandler
                        delete customDeleteHandler
                    }
                    """);
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:write_" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE app_user (
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        username VARCHAR(255) NOT NULL UNIQUE,
                        email VARCHAR(255) UNIQUE,
                        status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
                    );
                    CREATE TABLE external_account (
                        account_id VARCHAR(64) PRIMARY KEY,
                        display_name VARCHAR(255) NOT NULL
                    );
                    CREATE TABLE membership (
                        tenant_id INT NOT NULL,
                        account_id INT NOT NULL,
                        role_name VARCHAR(64) NOT NULL,
                        PRIMARY KEY (tenant_id, account_id)
                    );
                    CREATE TABLE generated_membership (
                        id INT AUTO_INCREMENT,
                        tenant_id INT NOT NULL,
                        role_name VARCHAR(64) NOT NULL,
                        PRIMARY KEY (id, tenant_id)
                    );
                    CREATE TABLE sync_record (
                        id INT PRIMARY KEY,
                        record_value VARCHAR(64) NOT NULL,
                        updated_at TIMESTAMP NOT NULL
                    );
                    INSERT INTO sync_record VALUES
                        (1, 'first', TIMESTAMP '2026-01-01 00:00:00'),
                        (2, 'second', TIMESTAMP '2026-01-02 00:00:00');
                    CREATE VIEW app_user_view AS
                        SELECT id, username, email, status FROM app_user;
                    """);
        }

        connector = new TestSqlConnector();
        connector.init(configuration());
        connector.schema();
    }

    @AfterMethod
    public void tearDown() {
        if (connector != null) {
            connector.dispose();
            connector = null;
        }
    }

    @Test
    public void testGeneratedUidCreateUpdateAndDelete() {
        var createdUid = connector.create(USER, Set.of(
                AttributeBuilder.build(Name.NAME, "new.user"),
                AttributeBuilder.build("username", "new.user"),
                AttributeBuilder.build("email", "new.user@example.com")), options());

        assertThat(createdUid.getUidValue()).isNotBlank();
        var created = get(USER, createdUid);
        assertThat(value(created, "username")).isEqualTo("new.user");
        assertThat(value(created, "status")).isEqualTo("ACTIVE");

        var emailReplacement = AttributeDeltaBuilder.build(
                "email", List.of("updated@example.com"));
        assertThat(connector.updateDelta(
                USER, createdUid, Set.of(emailReplacement), options()))
                .containsExactly(emailReplacement);
        assertThat(value(get(USER, createdUid), "email")).isEqualTo("updated@example.com");

        var removeEmail = new AttributeDeltaBuilder()
                .setName("email")
                .addValueToRemove("updated@example.com")
                .build();
        connector.updateDelta(USER, createdUid, Set.of(removeEmail), options());
        assertThat(value(get(USER, createdUid), "email")).isNull();

        connector.delete(USER, createdUid, options());
        assertThat(search(USER, uidFilter(createdUid))).isEmpty();
        assertThatThrownBy(() -> connector.delete(USER, createdUid, options()))
                .isInstanceOf(UnknownUidException.class);
    }

    @Test
    public void testSuppliedAndCompositeUids() {
        var externalUid = connector.create(EXTERNAL_ACCOUNT, Set.of(
                AttributeBuilder.build(Uid.NAME, "ext-100"),
                AttributeBuilder.build(Name.NAME, "ext-100"),
                AttributeBuilder.build("display_name", "External account")), options());
        assertThat(externalUid.getUidValue()).isEqualTo("ext-100");
        assertThat(value(get(EXTERNAL_ACCOUNT, externalUid), "display_name"))
                .isEqualTo("External account");

        var membershipUid = connector.create(MEMBERSHIP, Set.of(
                AttributeBuilder.build(Uid.NAME, "10.20"),
                AttributeBuilder.build(Name.NAME, "10.20"),
                AttributeBuilder.build("role_name", "owner")), options());
        assertThat(membershipUid.getUidValue()).isEqualTo("10.20");

        var replacement = AttributeDeltaBuilder.build("role_name", List.of("reviewer"));
        connector.updateDelta(MEMBERSHIP, membershipUid, Set.of(replacement), options());
        assertThat(value(get(MEMBERSHIP, membershipUid), "role_name")).isEqualTo("reviewer");

        connector.delete(MEMBERSHIP, membershipUid, options());
        assertThat(search(MEMBERSHIP, uidFilter(membershipUid))).isEmpty();

        var generatedMembershipUid = connector.create(GENERATED_MEMBERSHIP, Set.of(
                AttributeBuilder.build(Name.NAME, "generated-membership"),
                AttributeBuilder.build("tenant_id", 42),
                AttributeBuilder.build("role_name", "member")), options());
        assertThat(generatedMembershipUid.getUidValue()).endsWith(".42");
        assertThat(value(get(GENERATED_MEMBERSHIP, generatedMembershipUid), "role_name"))
                .isEqualTo("member");
        connector.delete(GENERATED_MEMBERSHIP, generatedMembershipUid, options());
    }

    @Test
    public void testValidationConstraintMappingRollbackAndReadOnlyView() {
        var existingUid = connector.create(USER, Set.of(
                AttributeBuilder.build(Name.NAME, "duplicate"),
                AttributeBuilder.build("username", "duplicate"),
                AttributeBuilder.build("email", "first@example.com")), options());

        assertThatThrownBy(() -> connector.create(USER, Set.of(
                AttributeBuilder.build(Name.NAME, "duplicate"),
                AttributeBuilder.build("username", "duplicate"),
                AttributeBuilder.build("email", "second@example.com")), options()))
                .isInstanceOf(AlreadyExistsException.class);
        assertThat(search(USER, null)).hasSize(1);

        var uidReplacement = AttributeDeltaBuilder.build(Uid.NAME, List.of("other-uid"));
        assertThatThrownBy(() -> connector.updateDelta(
                USER, existingUid, Set.of(uidReplacement), options()))
                .isInstanceOf(InvalidAttributeValueException.class);

        var missingUpdate = AttributeDeltaBuilder.build("email", List.of("missing@example.com"));
        assertThatThrownBy(() -> connector.updateDelta(
                USER, new Uid("999999"), Set.of(missingUpdate), options()))
                .isInstanceOf(UnknownUidException.class);

        assertThatThrownBy(() -> connector.create(USER, Set.of(
                AttributeBuilder.build(Name.NAME, "missing-username"),
                AttributeBuilder.build("email", "missing@example.com")), options()))
                .isInstanceOf(InvalidAttributeValueException.class)
                .hasMessageContaining("USERNAME");

        assertThatThrownBy(() -> connector.create(USER_VIEW, Collections.emptySet(), options()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testExplicitCreateHandlerTakesPrecedence() {
        connector.dispose();
        connector = new CustomCreateConnector();
        connector.init(configuration());
        connector.schema();

        var uid = connector.create(USER, Collections.emptySet(), options());

        assertThat(uid.getUidValue()).isEqualTo("custom-uid");
        assertThat(search(USER, null)).isEmpty();
    }

    @Test
    public void testGroovyHandlerDslRegistersWriteOverrides() {
        connector.dispose();
        connector = new GroovyCustomHandlerConnector();
        connector.init(configuration());
        connector.schema();

        var uid = connector.create(USER, Collections.emptySet(), options());
        assertThat(uid.getUidValue()).isEqualTo("groovy-uid");

        var replacement = AttributeDeltaBuilder.build("email", List.of("ignored@example.com"));
        assertThat(connector.updateDelta(USER, uid, Set.of(replacement), options()))
                .containsExactly(replacement);
        connector.delete(USER, uid, options());
        assertThat(search(USER, null)).isEmpty();
    }

    @Test
    public void testSyncSupportsNumericAndTimestampPaths() throws Exception {
        var timestampToken = connector.getLatestSyncToken(SYNC_RECORD);
        assertThat(timestampToken.getValue()).isInstanceOf(Long.class);

        var initialDeltas = new ArrayList<SyncDelta>();
        connector.sync(SYNC_RECORD, null, initialDeltas::add, options());
        assertThat(initialDeltas).hasSize(2);

        var previousToken = initialDeltas.getLast().getToken();
        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE sync_record
                    SET record_value = 'changed', updated_at = TIMESTAMP '2026-01-03 00:00:00'
                    WHERE id = 1
                    """);
        }

        var changedDeltas = new ArrayList<SyncDelta>();
        connector.sync(SYNC_RECORD, previousToken, changedDeltas::add, options());
        assertThat(changedDeltas).hasSize(1);
        assertThat(changedDeltas.getFirst().getUid().getUidValue()).isEqualTo("1");

        var userUid = connector.create(USER, Set.of(
                AttributeBuilder.build(Name.NAME, "sync.user"),
                AttributeBuilder.build("username", "sync.user")), options());
        SyncToken numericToken = connector.getLatestSyncToken(USER);
        assertThat(numericToken.getValue()).isInstanceOf(Number.class);
        assertThat(numericToken.getValue().toString()).isEqualTo(userUid.getUidValue());

        var numericDeltas = new ArrayList<SyncDelta>();
        connector.sync(USER, new SyncToken(0L), numericDeltas::add, options());
        assertThat(numericDeltas).hasSize(1);
        assertThat(numericDeltas.getFirst().getUid()).isEqualTo(userUid);
    }

    private SqlConnectorConfiguration configuration() {
        var configuration = new SqlConnectorConfiguration();
        configuration.setJdbcUrl(jdbcUrl);
        configuration.setUsername("sa");
        configuration.setPassword(new GuardedString("".toCharArray()));
        configuration.setPoolSize(5);
        configuration.setConnectionTimeout(10000);
        configuration.setScanTables(true);
        configuration.setScanViews(true);
        return configuration;
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
}
