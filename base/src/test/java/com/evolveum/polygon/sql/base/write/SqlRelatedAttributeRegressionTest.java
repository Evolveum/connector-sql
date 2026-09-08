/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regressions for schema overrides and JDBC join-key edge cases in related-table CRUD. */
@Test(singleThreaded = true)
public class SqlRelatedAttributeRegressionTest {

    private static final ObjectClass USERS = new ObjectClass("USERS");
    private static final Uid ALICE = new Uid("1");
    private static final OperationOptions OPTIONS = new OperationOptions(Map.of());
    private static final String PROFILE_SCHEMA = """
            CREATE TABLE users(id BIGINT PRIMARY KEY, username VARCHAR(30));
            CREATE TABLE profiles(user_id BIGINT PRIMARY KEY, bio VARCHAR(50), city VARCHAR(50),
                FOREIGN KEY(user_id) REFERENCES users(id));
            INSERT INTO users VALUES(1, 'alice');
            INSERT INTO profiles VALUES(1, 'original', 'Bratislava');
            """;

    private String jdbcUrl;
    private TestSqlConnector connector;

    @AfterMethod
    public void tearDown() throws Exception {
        if (connector != null) {
            connector.dispose();
            connector = null;
        }
        if (jdbcUrl != null) {
            execute("SHUTDOWN");
            jdbcUrl = null;
        }
    }

    @DataProvider
    public Object[][] bioNames() {
        return new Object[][] { { "BIO" }, { "biography" } };
    }

    @Test(dataProvider = "bioNames")
    public void rejectsNonCreatableEmbeddedFieldsIncludingNativeAlias(String field) throws Exception {
        initialize(PROFILE_SCHEMA, """
                objectClass('PROFILES') {
                    attribute('BIO') { connId { name 'biography'; creatable false } }
                }
                """);

        assertThatThrownBy(() -> connector.create(USERS, Set.of(
                AttributeBuilder.build(Name.NAME, "2"),
                AttributeBuilder.build("PROFILES", profile(AttributeBuilder.build(field, "forbidden")))), OPTIONS))
                .isInstanceOf(InvalidAttributeValueException.class)
                .hasMessageContaining("not creatable");
        assertThat(read()).extracting(object -> object.getUid().getUidValue()).containsExactly("1");
    }

    @Test(dataProvider = "bioNames")
    public void rejectsNonUpdatableEmbeddedFieldsAndRollsBackParent(String field) throws Exception {
        initialize(PROFILE_SCHEMA, """
                objectClass('PROFILES') {
                    attribute('BIO') { connId { name 'biography'; updatable false } }
                }
                """);

        assertThatThrownBy(() -> connector.updateDelta(USERS, ALICE, Set.of(
                AttributeDeltaBuilder.build("USERNAME", List.of("changed")),
                AttributeDeltaBuilder.build("PROFILES", List.of(
                        profile(AttributeBuilder.build(field, "forbidden"))))), OPTIONS))
                .isInstanceOf(InvalidAttributeValueException.class)
                .hasMessageContaining("not updatable");
        assertThat(stored("SELECT bio FROM profiles WHERE user_id = 1")).isEqualTo("original");
        assertThat(stored("SELECT username FROM users WHERE id = 1")).isEqualTo("alice");
    }

    @Test
    public void preservesOmittedReadOnlyFieldsAndAcceptsUnchangedValues() throws Exception {
        initialize(PROFILE_SCHEMA, """
                objectClass('PROFILES') {
                    attribute('BIO') { connId { updatable false } }
                }
                """);
        replaceProfile(profile(AttributeBuilder.build("CITY", "Prague")));
        assertThat(stored("SELECT bio FROM profiles WHERE user_id = 1")).isEqualTo("original");
        assertThat(stored("SELECT city FROM profiles WHERE user_id = 1")).isEqualTo("Prague");

        replaceProfile(profile(AttributeBuilder.build("BIO", "original"),
                AttributeBuilder.build("CITY", "Brno")));
        assertThat(stored("SELECT bio FROM profiles WHERE user_id = 1")).isEqualTo("original");
        assertThat(stored("SELECT city FROM profiles WHERE user_id = 1")).isEqualTo("Brno");
    }

    @Test
    public void validatesCreationOfNewChildRowsDuringParentUpdate() throws Exception {
        initialize(PROFILE_SCHEMA, """
                objectClass('PROFILES') {
                    attribute('BIO') { connId { creatable false } }
                }
                """);
        execute("DELETE FROM profiles");
        assertThatThrownBy(() -> replaceProfile(profile(AttributeBuilder.build("BIO", "forbidden"))))
                .isInstanceOf(InvalidAttributeValueException.class)
                .hasMessageContaining("not creatable");
        assertThat(stored("SELECT COUNT(*) FROM profiles")).isEqualTo("0");
    }

    @Test
    public void acceptsUnchangedReadOnlyDecimalsWithDifferentScale() throws Exception {
        initialize(PROFILE_SCHEMA.replace("bio VARCHAR(50)", "bio DECIMAL(10, 2)")
                .replace("'original'", "1.00"), """
                objectClass('PROFILES') {
                    attribute('BIO') { connId { updatable false } }
                }
                """);
        replaceProfile(profile(AttributeBuilder.build("BIO", BigDecimal.ONE),
                AttributeBuilder.build("CITY", "Brno")));
        assertThat(stored("SELECT bio FROM profiles WHERE user_id = 1")).isEqualTo("1.00");
        assertThat(stored("SELECT city FROM profiles WHERE user_id = 1")).isEqualTo("Brno");
    }

    @Test
    public void rejectsWritesToReadOnlyChildObjectClass() throws Exception {
        initialize(PROFILE_SCHEMA, "objectClass('PROFILES') { readOnly true }");
        assertThatThrownBy(() -> replaceProfile(profile(AttributeBuilder.build("BIO", "forbidden"))))
                .isInstanceOf(InvalidAttributeValueException.class)
                .hasMessageContaining("read-only");
        assertThat(stored("SELECT bio FROM profiles WHERE user_id = 1")).isEqualTo("original");
    }

    @DataProvider
    public Object[][] numericTypes() {
        return new Object[][] {
                { "BIGINT", "INTEGER" },
                { "INTEGER", "BIGINT" },
                { "DECIMAL(12, 2)", "DECIMAL(12, 4)" }
        };
    }

    @Test(dataProvider = "numericTypes")
    public void supportsRelatedCrudWithDifferentNumericJoinTypes(String parentType, String childType)
            throws Exception {
        initialize("""
                CREATE TABLE users(id %s PRIMARY KEY, username VARCHAR(30));
                CREATE TABLE emails(user_id %s, email VARCHAR(50), PRIMARY KEY(user_id, email),
                    FOREIGN KEY(user_id) REFERENCES users(id));
                INSERT INTO users VALUES(1, 'alice');
                INSERT INTO emails VALUES(1, 'first@example.com');
                """.formatted(parentType, childType), "");

        assertThat(values(read().getFirst(), "EMAILS")).containsExactly("first@example.com");
        var uid = read().getFirst().getUid();
        connector.updateDelta(USERS, uid, Set.of(new AttributeDeltaBuilder().setName("EMAILS")
                .addValueToAdd("second@example.com").addValueToRemove("first@example.com").build()), OPTIONS);
        assertThat(values(read().getFirst(), "EMAILS")).containsExactly("second@example.com");
        connector.delete(USERS, uid, OPTIONS);
        assertThat(read()).isEmpty();
        assertThat(stored("SELECT COUNT(*) FROM emails")).isEqualTo("0");

        var created = connector.create(USERS, Set.of(AttributeBuilder.build(Name.NAME, "2"),
                AttributeBuilder.build("EMAILS", "new@example.com")), OPTIONS);
        assertThat(values(read().getFirst(), "EMAILS")).containsExactly("new@example.com");
        connector.delete(USERS, created, OPTIONS);
    }

    @Test
    public void initializesRelatedAttributesUsingCreateNotUpdatePermissions() throws Exception {
        initialize(PROFILE_SCHEMA, """
                objectClass('USERS') {
                    attribute('PROFILES') { connId { updatable false } }
                }
                """);
        var uid = connector.create(USERS, Set.of(AttributeBuilder.build(Name.NAME, "2"),
                AttributeBuilder.build("PROFILES", profile(AttributeBuilder.build("CITY", "Prague")))), OPTIONS);
        assertThat(stored("SELECT city FROM profiles WHERE user_id = 2")).isEqualTo("Prague");

        assertThatThrownBy(() -> connector.updateDelta(USERS, uid, Set.of(
                AttributeDeltaBuilder.build("USERNAME", List.of("must-rollback")),
                AttributeDeltaBuilder.build("PROFILES", List.of(
                        profile(AttributeBuilder.build("CITY", "Brno"))))), OPTIONS))
                .isInstanceOf(InvalidAttributeValueException.class).hasMessageContaining("not updatable");
        assertThat(stored("SELECT username FROM users WHERE id = 2")).isNull();
        assertThat(stored("SELECT city FROM profiles WHERE user_id = 2")).isEqualTo("Prague");
    }

    @Test
    public void rejectsNonCreatableRelatedAttributeAndRollsBackParent() throws Exception {
        initialize(PROFILE_SCHEMA, """
                objectClass('USERS') {
                    attribute('PROFILES') { connId { creatable false } }
                }
                """);
        assertThatThrownBy(() -> connector.create(USERS, Set.of(
                AttributeBuilder.build(Name.NAME, "2"),
                AttributeBuilder.build("PROFILES", profile(AttributeBuilder.build("CITY", "Prague")))), OPTIONS))
                .isInstanceOf(InvalidAttributeValueException.class).hasMessageContaining("not creatable");
        assertThat(stored("SELECT COUNT(*) FROM users WHERE id = 2")).isEqualTo("0");
        replaceProfile(profile(AttributeBuilder.build("CITY", "Brno")));
        assertThat(stored("SELECT city FROM profiles WHERE user_id = 1")).isEqualTo("Brno");
    }

    @Test
    public void resolvesNativeJoinValuesAfterPrimaryUpdate() throws Exception {
        initialize("""
                CREATE TABLE users(id BIGINT PRIMARY KEY, username VARCHAR(30) UNIQUE);
                CREATE TABLE emails(username VARCHAR(30), email VARCHAR(50), PRIMARY KEY(username, email),
                    FOREIGN KEY(username) REFERENCES users(username));
                INSERT INTO users VALUES(1, 'alice');
                """, "");

        connector.updateDelta(USERS, ALICE, Set.of(
                AttributeDeltaBuilder.build("USERNAME", List.of("renamed")),
                AttributeDeltaBuilder.build("EMAILS", List.of("new@example.com"))), OPTIONS);
        assertThat(stored("SELECT username FROM emails WHERE email = 'new@example.com'"))
                .isEqualTo("renamed");
    }

    @Test
    public void acceptsEquivalentDecimalUidForUpdateAndDelete() throws Exception {
        initialize(PROFILE_SCHEMA.replace("BIGINT", "DECIMAL(12, 2)"), "");

        connector.updateDelta(USERS, ALICE, Set.of(
                AttributeDeltaBuilder.build("USERNAME", List.of("updated")),
                AttributeDeltaBuilder.build("PROFILES", List.of(
                        profile(AttributeBuilder.build("CITY", "Prague"))))), OPTIONS);
        assertThat(stored("SELECT username FROM users WHERE id = 1")).isEqualTo("updated");
        assertThat(stored("SELECT city FROM profiles WHERE user_id = 1")).isEqualTo("Prague");

        connector.delete(USERS, ALICE, OPTIONS);
        assertThat(stored("SELECT COUNT(*) FROM users")).isEqualTo("0");
        assertThat(stored("SELECT COUNT(*) FROM profiles")).isEqualTo("0");
    }

    @Test
    public void ignoresIncompleteCompositeParentJoinKeys() throws Exception {
        initialize("""
                CREATE TABLE users(id BIGINT PRIMARY KEY, tenant INTEGER, username VARCHAR(30),
                    UNIQUE(tenant, username));
                CREATE TABLE emails(tenant INTEGER, username VARCHAR(30), email VARCHAR(50),
                    PRIMARY KEY(tenant, username, email),
                    FOREIGN KEY(tenant, username) REFERENCES users(tenant, username));
                INSERT INTO users VALUES(1, 10, NULL), (2, 10, 'bob'), (3, NULL, 'charlie');
                INSERT INTO emails VALUES(10, 'bob', 'bob@example.com');
                """, "");

        var users = read();
        assertThat(users).hasSize(3);
        for (var user : users) {
            if (user.getUid().getUidValue().equals("2")) {
                assertThat(values(user, "EMAILS")).containsExactly("bob@example.com");
            } else {
                assertThat(values(user, "EMAILS")).isEmpty();
            }
        }
    }

    @Test
    public void widerParentKeyWithoutChildMatchDoesNotBreakSearch() throws Exception {
        initialize("""
                CREATE TABLE users(id BIGINT PRIMARY KEY, username VARCHAR(30));
                CREATE TABLE emails(user_id INTEGER, email VARCHAR(50), PRIMARY KEY(user_id, email),
                    FOREIGN KEY(user_id) REFERENCES users(id));
                INSERT INTO users VALUES(2147483648, 'alice');
                """, "");
        assertThat(read()).hasSize(1);
        assertThat(values(read().getFirst(), "EMAILS")).isEmpty();
    }

    @Test
    public void resolvesJunctionReferencesWithDifferentNumericJoinTypes() throws Exception {
        initialize("""
                CREATE TABLE users(id BIGINT PRIMARY KEY, username VARCHAR(30));
                CREATE TABLE roles(id INTEGER PRIMARY KEY, name VARCHAR(30));
                CREATE TABLE membership(user_id INTEGER, role_id BIGINT,
                    PRIMARY KEY(user_id, role_id),
                    FOREIGN KEY(user_id) REFERENCES users(id),
                    FOREIGN KEY(role_id) REFERENCES roles(id));
                INSERT INTO users VALUES(1, 'alice');
                INSERT INTO roles VALUES(2, 'reviewer');
                INSERT INTO membership VALUES(1, 2);
                """, "");
        var references = values(read().getFirst(), "ROLES");
        assertThat(references).hasSize(1);
        var reference = (ConnectorObjectReference) references.getFirst();
        assertThat(AttributeUtil.getUidAttribute(reference.getValue().getAttributes()).getUidValue())
                .isEqualTo("2");
        connector.delete(USERS, ALICE, OPTIONS);
        assertThat(stored("SELECT COUNT(*) FROM membership")).isEqualTo("0");
        assertThat(stored("SELECT COUNT(*) FROM roles")).isEqualTo("1");
    }

    @Test
    public void usesRenamedScalarAttributeForAllCrudOperations() throws Exception {
        initialize("""
                CREATE TABLE users(id BIGINT PRIMARY KEY, username VARCHAR(30));
                CREATE TABLE emails(user_id BIGINT, email VARCHAR(50), PRIMARY KEY(user_id, email),
                    FOREIGN KEY(user_id) REFERENCES users(id));
                """, "objectClass('USERS') { attribute('EMAILS') { connId { name 'mail' } } }");
        var uid = connector.create(USERS, Set.of(AttributeBuilder.build(Name.NAME, "1"),
                AttributeBuilder.build("mail", "first@example.com")), OPTIONS);
        assertThat(values(read().getFirst(), "mail")).containsExactly("first@example.com");
        assertThat(read().getFirst().getAttributeByName("EMAILS")).isNull();
        connector.updateDelta(USERS, uid, Set.of(new AttributeDeltaBuilder().setName("mail")
                .addValueToRemove("first@example.com").addValueToAdd("second@example.com").build()), OPTIONS);
        assertThat(values(read().getFirst(), "mail")).containsExactly("second@example.com");
        connector.delete(USERS, uid, OPTIONS);
        assertThat(stored("SELECT COUNT(*) FROM emails")).isEqualTo("0");
    }

    @Test
    public void usesRenamedEmbeddedAttributeForReadsAndWrites() throws Exception {
        initialize(PROFILE_SCHEMA,
                "objectClass('USERS') { attribute('PROFILES') { connId { name 'profile' } } }");
        var replacement = profile(AttributeBuilder.build("BIO", "changed"));
        connector.updateDelta(USERS, ALICE, Set.of(
                AttributeDeltaBuilder.build("profile", List.of(replacement))), OPTIONS);
        assertThat(values(read().getFirst(), "profile")).hasSize(1);
        assertThat(read().getFirst().getAttributeByName("PROFILES")).isNull();
        assertThat(stored("SELECT bio FROM profiles WHERE user_id = 1")).isEqualTo("changed");
        connector.delete(USERS, ALICE, OPTIONS);
        assertThat(stored("SELECT COUNT(*) FROM profiles")).isEqualTo("0");
    }

    private void initialize(String sql, String schemaScript) throws Exception {
        jdbcUrl = "jdbc:h2:mem:related_regression_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        execute(sql);
        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("sa");
        config.setPassword(new GuardedString(new char[0]));
        config.setScanTables(true);
        config.setScanViews(false);
        config.setDevelopmentMode(false);
        connector = new TestSqlConnector(schemaScript);
        connector.init(config);
        connector.schema();
    }

    private void replaceProfile(EmbeddedObject value) {
        connector.updateDelta(USERS, ALICE, Set.of(
                AttributeDeltaBuilder.build("PROFILES", List.of(value))), OPTIONS);
    }

    private static EmbeddedObject profile(Attribute... attributes) {
        return new EmbeddedObject(new ObjectClass("PROFILES"), Set.of(attributes));
    }

    private List<ConnectorObject> read() {
        var result = new ArrayList<ConnectorObject>();
        connector.executeQuery(USERS, null, object -> { result.add(object); return true; }, OPTIONS);
        return result;
    }

    private static List<Object> values(ConnectorObject object, String attributeName) {
        var attribute = object.getAttributeByName(attributeName);
        return attribute == null || attribute.getValue() == null ? List.of() : attribute.getValue();
    }

    private void execute(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String stored(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                var statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static final class TestSqlConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        private final String schemaScript;

        private TestSqlConnector(String schemaScript) {
            super(false);
            this.schemaScript = schemaScript;
        }

        @Override
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) {
            if (!schemaScript.isEmpty()) {
                loader.load(schemaScript);
            }
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader loader) {
            // Use built-in handlers.
        }
    }
}
