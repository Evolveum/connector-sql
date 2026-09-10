/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.test.contract;

import com.evolveum.polygon.conndev.spi.*;
import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilder;
import com.evolveum.polygon.sql.base.dev.SqlDevelopmentMode;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Common behavior contract executed against every supported real database configuration.
 * Database-specific tests remain responsible for exact dialect syntax and vendor-only types.
 */
public abstract class AbstractSqlConnectorContractTest {

    private static final String USER = "contract_user";
    private static final String GROUP = "contract_group";
    private static final String EXTERNAL = "contract_external";
    private static final String ADDRESS = "contract_address";
    private static final String PROFILE = "contract_user_profile";
    private static final String EMAILS = "contract_user_email";
    private static final String PHONES = "contract_user_phone";
    private static final String USER_ALIASES = "contract_user_alias";
    private static final String COMPOSITE = "contract_composite";
    private static final String COMPOSITE_TAGS = "contract_composite_tag";
    private static final String USER_VIEW = "contract_user_view";

    private static final OperationOptions OPTIONS = new OperationOptions(Collections.emptyMap());
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SqlTestDatabase database;
    private ContractConnector connector;

    protected abstract SqlTestDatabase createDatabase() throws Exception;

    @BeforeClass(alwaysRun = true)
    public final void setUpDatabaseContract() throws Exception {
        var candidate = createDatabase();
        try {
            if (candidate.capabilities().external()) {
                database = ExternalDatabaseTestSupport.connect(
                        candidate.database().name(), () -> initialize(candidate));
            } else {
                database = initialize(candidate);
            }
        } catch (Exception e) {
            closeQuietly(candidate);
            throw e;
        }

        connector = new ContractConnector();
        connector.init(database.configuration(true));
        connector.schema();
    }

    @BeforeMethod(alwaysRun = true)
    public final void resetDatabaseContractData() throws Exception {
        database.initializeSchema();
    }

    @AfterClass(alwaysRun = true)
    public final void tearDownDatabaseContract() throws Exception {
        if (connector != null) {
            connector.dispose();
            connector = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }

    @Test
    public final void coordinatesRelatedCrudWithSingleConnectionPool() {
        var configuration = database.configuration(true);
        configuration.setPoolSize(1);
        var singleConnectionConnector = new ContractConnector();
        try {
            singleConnectionConnector.init(configuration);
            singleConnectionConnector.schema();
            var userClass = objectClass(USER);
            var username = attributeName(USER, "username");
            var emails = attributeName(USER, EMAILS);
            var uid = singleConnectionConnector.create(userClass, Set.of(
                    AttributeBuilder.build(Name.NAME, "contract-one-connection"),
                    AttributeBuilder.build(username, "one-connection"),
                    AttributeBuilder.build(emails, "first@example.com")), OPTIONS);
            singleConnectionConnector.updateDelta(userClass, uid, Set.of(
                    AttributeDeltaBuilder.build(username, List.of("one-connection-updated")),
                    AttributeDeltaBuilder.build(emails, List.of("second@example.com"))), OPTIONS);

            // Independent search uses the ordinary connector; its batching is not a write scope.
            var updated = get(userClass, uid);
            assertThat(value(updated, username)).isEqualTo("one-connection-updated");
            assertThat(values(updated, emails)).containsExactly("second@example.com");
            singleConnectionConnector.delete(userClass, uid, OPTIONS);
            assertThat(search(userClass, uidFilter(uid))).isEmpty();
        } finally {
            singleConnectionConnector.dispose();
        }
    }

    @Test
    public final void usesSharedCrudCoordinators() {
        var handler = connector.context().handlerFor(objectClass(USER));
        assertThat(handler.checkSupported(ObjectCreateOperation.class))
                .isInstanceOf(CreateOperationStrategyHandler.class);
        assertThat(handler.checkSupported(ObjectUpdateOperation.class))
                .isInstanceOf(UpdateOperationStrategyHandler.class);
        assertThat(handler.checkSupported(ObjectDeleteOperation.class))
                .isInstanceOf(DeleteOperationStrategyHandler.class);
    }

    @Test
    public final void validatesParentForChildOnlyUpdate() {
        assertThatThrownBy(() -> connector.updateDelta(objectClass(USER), new Uid("999999"), Set.of(
                AttributeDeltaBuilder.build(attributeName(USER, EMAILS), List.of("missing@example.com"))), OPTIONS))
                .isInstanceOf(UnknownUidException.class);
    }

    @Test
    public final void combinesPrimaryAndChildResultDeltas() {
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var emails = attributeName(USER, EMAILS);
        var uid = connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-result-deltas"),
                AttributeBuilder.build(username, "result-deltas")), OPTIONS);
        var changes = Set.of(
                AttributeDeltaBuilder.build(username, List.of("result-deltas-updated")),
                AttributeDeltaBuilder.build(emails, List.of("result@example.com")));

        assertThat(connector.updateDelta(userClass, uid, changes, OPTIONS))
                .containsExactlyInAnyOrderElementsOf(changes);
        var updated = get(userClass, uid);
        assertThat(value(updated, username)).isEqualTo("result-deltas-updated");
        assertThat(values(updated, emails)).containsExactly("result@example.com");
    }

    @Test
    public final void discoversSharedSchemaContract() {
        assertThat(schemaNames()).contains(
                objectClassInfo(USER).getType(),
                objectClassInfo(GROUP).getType(),
                objectClassInfo(EXTERNAL).getType(),
                objectClassInfo(COMPOSITE).getType(),
                objectClassInfo(USER_VIEW).getType());

        var user = objectClassInfo(USER);
        assertThat(attributeInfo(user, "id").isRequired()).isTrue();
        assertThat(attributeInfo(user, "username").isRequired()).isTrue();
        assertThat(attributeInfo(user, "email").isRequired()).isFalse();
        assertThat(attributeInfo(user, "active")).isNotNull();
        assertThat(attributeInfo(user, "quota")).isNotNull();
        assertThat(attributeInfo(user, PROFILE).isMultiValued()).isFalse();
        assertThat(attributeInfo(user, EMAILS).isMultiValued()).isTrue();
        assertThat(attributeInfo(user, PHONES).isMultiValued()).isTrue();
        if (database.capabilities().supportsNonPrimaryForeignKeyMetadata()) {
            assertThat(attributeInfo(user, USER_ALIASES).isMultiValued()).isTrue();
        }
        assertThat(objectClassInfo(PROFILE).isEmbedded()).isTrue();
        assertThat(objectClassInfo(PHONES).isEmbedded()).isTrue();

        var composite = objectClassInfo(COMPOSITE);
        assertThat(attributeInfo(composite, "tenant_id").isRequired()).isTrue();
        assertThat(attributeInfo(composite, "record_id").isRequired()).isTrue();
        assertThat(attributeInfo(composite, COMPOSITE_TAGS).isMultiValued()).isTrue();
    }

    @Test
    public final void readsFlatJoinsWithSeparateAliasesAndFiltersButNoWrites() {
        setJoinedPhones("work", "home");
        var script = """
                objectClass('FlatUser') {
                    sql {
                        table '%s'
                        join {
                            table '%s'
                            prefixAttributes 'work_'
                            skipAttributes 'user_id'
                            where { q -> q.column('phone_type').eq('work') }
                        }
                        join {
                            table '%s'
                            prefixAttributes 'home_'
                            skipAttributes 'user_id'
                            where { q -> q.column('phone_type').eq('home') }
                        }
                    }
                }
                """.formatted(objectClass(USER).getObjectClassValue(),
                objectClass(PHONES).getObjectClassValue(), objectClass(PHONES).getObjectClassValue());
        var joined = joinedConnector(script);
        try {
            var flat = new ObjectClass("FlatUser");
            var info = joined.schema().findObjectClassInfo("FlatUser");
            var work = attributeInfo(info, "work_phone_number").getName();
            var home = attributeInfo(info, "home_phone_number").getName();
            assertThat(info.getAttributeInfo()).noneMatch(attribute ->
                    attribute.isCreateable() || attribute.isUpdateable());
            assertThat(info.getAttributeInfo()).noneMatch(attribute ->
                    attribute.getName().equalsIgnoreCase("work_user_id")
                            || attribute.getName().equalsIgnoreCase(PHONES));
            assertThat(attributeInfo(info, "work_phone_number").isRequired()).isFalse();
            var rows = search(joined, flat, null);
            assertThat(rows).hasSize(2);
            var alice = rows.stream().filter(row -> "1".equals(row.getUid().getUidValue())).findFirst().orElseThrow();
            assertThat(value(alice, work)).isEqualTo("111");
            assertThat(value(alice, home)).isEqualTo("222");
            var bob = rows.stream().filter(row -> "2".equals(row.getUid().getUidValue())).findFirst().orElseThrow();
            assertThat(value(bob, work)).isNull();
            assertThat(value(bob, home)).isNull();
            assertThat(search(joined, flat, FilterBuilder.and(uidFilter(new Uid("1")),
                    FilterBuilder.equalTo(AttributeBuilder.build(home, "222")))))
                    .hasSize(1);
            assertThat(search(joined, flat, FilterBuilder.equalTo(AttributeBuilder.build(work, "222"))))
                    .isEmpty();
            assertThat(search(joined, flat, FilterBuilder.equalTo(AttributeBuilder.build(work))))
                    .extracting(row -> row.getUid().getUidValue()).containsExactly("2");
            assertThatThrownBy(() -> joined.create(flat, Set.of(AttributeBuilder.build(Name.NAME, "new")), OPTIONS))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> joined.updateDelta(flat, new Uid("1"), Set.of(
                    AttributeDeltaBuilder.build(work, List.of("changed"))), OPTIONS))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> joined.delete(flat, new Uid("1"), OPTIONS))
                    .isInstanceOf(UnsupportedOperationException.class);
        } finally {
            joined.dispose();
        }
        // The ordinary object class for the same table retains its CRUD implementation.
        setJoinedPhones("changed", "home");
    }

    @Test
    public final void readsFlatViewJoinWithExplicitOnAndDeclaredAttributeRename() {
        var script = """
                objectClass('FlatView') {
                    sql {
                        table '%s'
                        join {
                            table '%s'
                            prefixAttributes 'view_'
                            skipAttributes 'id'
                            on { j -> j.left().column('id').eq(j.right().column('id')) }
                            where { q -> q.column('username').eq('alice') }
                        }
                    }
                    attribute('view_%s') { connId { name 'viewLogin' } }
                }
                """.formatted(objectClass(USER).getObjectClassValue(),
                objectClass(USER_VIEW).getObjectClassValue(), attributeName(USER_VIEW, "username"));
        var joined = joinedConnector(script);
        try {
            var rows = search(joined, new ObjectClass("FlatView"), null);
            assertThat(rows).hasSize(2);
            assertThat(search(joined, new ObjectClass("FlatView"),
                    FilterBuilder.equalTo(AttributeBuilder.build("viewLogin", "alice"))))
                    .hasSize(1).first().extracting(row -> value(row, "viewLogin")).isEqualTo("alice");
        } finally {
            joined.dispose();
        }
    }

    @Test
    public final void readsFlatJoinUsingEveryCompositeForeignKeyColumn() {
        connector.updateDelta(objectClass(COMPOSITE), new Uid("1.1"), Set.of(
                AttributeDeltaBuilder.build(attributeName(COMPOSITE, COMPOSITE_TAGS), List.of("selected"))), OPTIONS);
        // Each distractor shares one key component. Omitting either equality would multiply the rows.
        for (var uid : List.of("1.2", "2.1")) {
            connector.create(objectClass(COMPOSITE), Set.of(
                    AttributeBuilder.build(Uid.NAME, uid), AttributeBuilder.build(Name.NAME, uid),
                    AttributeBuilder.build(attributeName(COMPOSITE, COMPOSITE_TAGS), List.of("selected"))), OPTIONS);
        }
        var script = """
                objectClass('FlatComposite') {
                    sql {
                        table '%s'
                        join {
                            table '%s'
                            prefixAttributes 'joined_'
                            skipAttributes 'tenant_id', 'record_id'
                            where { q -> q.column('tag_value').eq('selected') }
                        }
                    }
                }
                """.formatted(objectClass(COMPOSITE).getObjectClassValue(),
                connector.context().getTableInfos().values().stream()
                        .filter(table -> table.getName().equalsIgnoreCase(COMPOSITE_TAGS)).findFirst().orElseThrow().getName());
        var joined = joinedConnector(script);
        try {
            var rows = search(joined, new ObjectClass("FlatComposite"), uidFilter(new Uid("1.1")));
            var info = joined.schema().findObjectClassInfo("FlatComposite");
            assertThat(rows).hasSize(1);
            assertThat(value(rows.getFirst(), attributeInfo(info, "joined_tag_value").getName())).isEqualTo("selected");
        } finally {
            joined.dispose();
        }
    }

    @Test
    public final void rejectsMultiRowFlatJoinsBeforeDeliveringObjects() {
        setJoinedPhones("work", "work");
        var joined = joinedConnector("""
                objectClass('FlatUser') {
                    sql {
                        table '%s'
                        join {
                            table '%s'
                            prefixAttributes 'phone_'
                            where { q -> q.column('phone_type').eq('work') }
                        }
                    }
                }
                """.formatted(objectClass(USER).getObjectClassValue(), objectClass(PHONES).getObjectClassValue()));
        try {
            var delivered = new ArrayList<ConnectorObject>();
            assertThat(search(joined, new ObjectClass("FlatUser"), uidFilter(new Uid("2"))))
                    .hasSize(1);
            assertThatThrownBy(() -> joined.executeQuery(new ObjectClass("FlatUser"), null, delivered::add, OPTIONS))
                    .isInstanceOf(ConnectorException.class).hasMessageContaining("multiple matching rows");
            assertThat(delivered).isEmpty();
        } finally {
            joined.dispose();
        }
    }

    @Test
    public final void readsLocalizedViewsUsingAllTypeKeysAndSeparateLanguageJoins() throws Exception {
        try (var connection = connector.context().getConnection();
             var fixture = new LocalizedViewJoinFixture(connection.getConnection())) {
            var joined = joinedConnector(localizedViewSchema(fixture), false);
            try {
                var flat = new ObjectClass("LocalizedUnit");
                var info = joined.schema().findObjectClassInfo("LocalizedUnit");
                var type = attributeInfo(info, "type_type_name").getName();
                var english = attributeInfo(info, "en_label_text").getName();
                var french = attributeInfo(info, "fr_label_text").getName();
                var german = attributeInfo(info, "de_label_text").getName();

                // Repeated unit/type numbers across tenants and sources must not multiply or mix rows.
                assertThat(search(joined, flat, null)).extracting(
                        row -> row.getUid().getUidValue(),
                        row -> value(row, type), row -> value(row, english),
                        row -> value(row, french), row -> value(row, german))
                        .containsExactly(
                                tuple("north.1", "Department", "North team", "Equipe nord", "Nordteam"),
                                tuple("north.2", "Project", "Project team", null, null),
                                tuple("north.3", "Office", "Office team", null, null),
                                tuple("north.4", null, "Unknown type team", null, null),
                                tuple("north.5", "Department", null, null, null),
                                tuple("north.6", "Department", null, "Francais seulement", null),
                                tuple("south.1", "Division", "South team", "Equipe sud", "Suedteam"));

                assertThat(search(joined, flat, uidFilter(new Uid("south.1"))))
                        .hasSize(1).first().extracting(row -> value(row, type)).isEqualTo("Division");
                assertThat(search(joined, flat, FilterBuilder.and(uidFilter(new Uid("north.2")),
                        FilterBuilder.equalTo(AttributeBuilder.build(type, "Project")))))
                        .hasSize(1);
                assertThat(search(joined, flat, FilterBuilder.equalTo(AttributeBuilder.build(french, "Equipe nord"))))
                        .extracting(row -> row.getUid().getUidValue()).containsExactly("north.1");
                assertThat(search(joined, flat, FilterBuilder.equalTo(AttributeBuilder.build(english, "Equipe nord"))))
                        .isEmpty();
                assertThat(search(joined, flat, FilterBuilder.equalTo(AttributeBuilder.build(english))))
                        .extracting(row -> row.getUid().getUidValue()).containsExactly("north.5", "north.6");
                assertThat(info.getAttributeInfo()).noneMatch(attribute ->
                        attribute.isCreateable() || attribute.isUpdateable());
            } finally {
                joined.dispose();
            }
        }
    }

    @DataProvider
    public Object[][] duplicateLocalizedViewRows() {
        return new Object[][] {
                { "INSERT INTO contract_unit_type VALUES ('north', 10, 100, 'Duplicate type')" },
                { "INSERT INTO contract_unit_label VALUES ('north', 1, 'en', 'Duplicate label')" }
        };
    }

    @Test(dataProvider = "duplicateLocalizedViewRows")
    public final void rejectsDuplicateLocalizedViewMatchesBeforeReturningObjects(String duplicateSql) throws Exception {
        try (var connection = connector.context().getConnection();
             var fixture = new LocalizedViewJoinFixture(connection.getConnection())) {
            fixture.execute(duplicateSql);
            var joined = joinedConnector(localizedViewSchema(fixture), false);
            try {
                var delivered = new ArrayList<ConnectorObject>();
                assertThatThrownBy(() -> joined.executeQuery(
                        new ObjectClass("LocalizedUnit"), null, delivered::add, OPTIONS))
                        .isInstanceOf(ConnectorException.class).hasMessageContaining("multiple matching rows");
                assertThat(delivered).isEmpty();
            } finally {
                joined.dispose();
            }
        }
    }

    private String localizedViewSchema(LocalizedViewJoinFixture fixture) throws Exception {
        // Oracle reports INTEGER differently from other drivers. The explicit composite UID
        // must use the same native mapping as the separately exposed unit_key column.
        var root = new SqlSchemaDetector(connector.context()).discover(List.of(
                new SqlSchemaDetector.TableRef(null, fixture.identifier("contract_unit_v")))).getFirst();
        var unitKey = root.getColumns().stream()
                .filter(column -> column.getName().equalsIgnoreCase("unit_key")).findFirst().orElseThrow();
        return """
                import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping

                objectClass('LocalizedUnit') {
                    sql {
                        table '%s'
                        join {
                            table '%s'
                            prefixAttributes 'type_'
                            skipAttributes 'tenant_key', 'kind_key', 'source_key'
                            on { j ->
                                j.left().column('tenant_key').eq(j.right().column('tenant_key'))
                                j.left().column('kind_key').eq(j.right().column('kind_key'))
                                j.left().column('source_key').eq(j.right().column('source_key'))
                            }
                        }
                        join {
                            table '%s'
                            prefixAttributes 'en_'
                            skipAttributes 'tenant_key', 'unit_key', 'locale_code'
                            on { j ->
                                j.left().column('tenant_key').eq(j.right().column('tenant_key'))
                                j.left().column('unit_key').eq(j.right().column('unit_key'))
                            }
                            where { q -> q.column('locale_code').eq('en') }
                        }
                        join {
                            table '%s'
                            prefixAttributes 'fr_'
                            skipAttributes 'tenant_key', 'unit_key', 'locale_code'
                            on { j ->
                                j.left().column('tenant_key').eq(j.right().column('tenant_key'))
                                j.left().column('unit_key').eq(j.right().column('unit_key'))
                            }
                            where { q -> q.column('locale_code').eq('fr') }
                        }
                        join {
                            table '%s'
                            prefixAttributes 'de_'
                            skipAttributes 'tenant_key', 'unit_key', 'locale_code'
                            on { j ->
                                j.left().column('tenant_key').eq(j.right().column('tenant_key'))
                                j.left().column('unit_key').eq(j.right().column('unit_key'))
                            }
                            where { q -> q.column('locale_code').eq('de') }
                        }
                    }
                    // Views have no JDBC primary keys, so explicitly map the two-part root UID.
                    attribute('%s') {
                        connId { name '__UID__' }
                        sql { additionalColumns().column('%s', SqlSchemaValueMapping.%s) }
                    }
                }
                """.formatted(fixture.identifier("contract_unit_v"), fixture.identifier("contract_unit_type_v"),
                fixture.identifier("contract_unit_label_v"), fixture.identifier("contract_unit_label_v"),
                fixture.identifier("contract_unit_label_v"), fixture.identifier("tenant_key"),
                fixture.identifier("unit_key"), unitKey.getValueMapping().name());
    }

    private void setJoinedPhones(String firstType, String secondType) {
        connector.updateDelta(objectClass(USER), new Uid("1"), Set.of(
                AttributeDeltaBuilder.build(attributeName(USER, PHONES), List.of(
                        embedded(PHONES, AttributeBuilder.build(attributeName(PHONES, "phone_number"), "111"),
                                AttributeBuilder.build(attributeName(PHONES, "phone_type"), firstType)),
                        embedded(PHONES, AttributeBuilder.build(attributeName(PHONES, "phone_number"), "222"),
                                AttributeBuilder.build(attributeName(PHONES, "phone_type"), secondType))))), OPTIONS);
    }

    @Test
    public final void exposesOnlyExplicitlyListedAttributesOfFlatJoins() {
        setJoinedPhones("work", "home");
        var schema = connector.context().getTableInfos().get(USER).getSchema();
        var joined = joinedConnector("""
                objectClass('FlatSelected') {
                    sql {
                        schema '%s'
                        table '%s'
                        join {
                            table '%s'
                            prefixAttributes 'work_'
                            where { q -> q.column('phone_type').eq('work') }
                        }
                    }
                    attribute('%s') { connId { name '__UID__' } }
                    attribute('work_%s') { connId { name 'workPhone' } }
                }
                """.formatted(schema != null ? schema : "", objectClass(USER).getObjectClassValue(),
                objectClass(PHONES).getObjectClassValue(),
                attributeInfo(objectClassInfo(USER), Uid.NAME).getNativeName(),
                attributeInfo(objectClassInfo(PHONES), "phone_number").getNativeName()), true, true);
        try {
            var flat = new ObjectClass("FlatSelected");
            assertThat(joined.schema().findObjectClassInfo(flat.getObjectClassValue()).getAttributeInfo())
                    .extracting(AttributeInfo::getName).containsExactlyInAnyOrder(Uid.NAME, Name.NAME, "workPhone");
            var rows = search(joined, flat, null);
            assertThat(rows).extracting(row -> row.getUid().getUidValue(), row -> value(row, "workPhone"))
                    .containsExactlyInAnyOrder(tuple("1", "111"), tuple("2", null));
            assertThat(search(joined, flat, FilterBuilder.equalTo(AttributeBuilder.build("workPhone", "111"))))
                    .extracting(row -> row.getUid().getUidValue()).containsExactly("1");
        } finally {
            joined.dispose();
        }
    }

    @Test
    public final void pagesFlatJoinsAndHonorsHandlerStop() throws Exception {
        try (var connection = connector.context().getConnection();
             var insert = connection.getConnection().prepareStatement(
                     "INSERT INTO " + objectClass(USER).getObjectClassValue() + " (username) VALUES (?)")) {
            for (int i = 0; i < 205; i++) {
                insert.setString(1, "flat-page-" + i);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        var joined = joinedConnector("""
                objectClass('FlatPage') { sql {
                    table '%s'
                    join { table '%s'; prefixAttributes 'profile_' }
                } }
                """.formatted(objectClass(USER).getObjectClassValue(), objectClass(PROFILE).getObjectClassValue()));
        try {
            var rows = search(joined, new ObjectClass("FlatPage"), null);
            assertThat(rows).hasSize(207);
            assertThat(rows.stream().map(row -> row.getUid().getUidValue()).distinct()).hasSize(207);
            var delivered = new ArrayList<ConnectorObject>();
            joined.executeQuery(new ObjectClass("FlatPage"), null, row -> {
                delivered.add(row);
                return false;
            }, OPTIONS);
            assertThat(delivered).hasSize(1);
        } finally {
            joined.dispose();
        }
    }

    private JoinedConnector joinedConnector(String script) {
        return joinedConnector(script, true);
    }

    private JoinedConnector joinedConnector(String script, boolean discovery) {
        return joinedConnector(script, discovery, false);
    }

    private JoinedConnector joinedConnector(String script, boolean discovery, boolean onlyExplicitlyListed) {
        var joined = new JoinedConnector(script, onlyExplicitlyListed);
        var config = database.configuration(false);
        config.setPoolSize(1);
        config.setScanTables(discovery);
        config.setScanViews(discovery);
        try {
            joined.init(config);
            joined.schema();
            return joined;
        } catch (RuntimeException e) {
            joined.dispose();
            throw e;
        }
    }

    private static List<ConnectorObject> search(JoinedConnector connector, ObjectClass objectClass, Filter filter) {
        var result = new ArrayList<ConnectorObject>();
        connector.executeQuery(objectClass, filter, result::add, OPTIONS);
        return result;
    }

    @Test
    public final void discoversOnlyConfiguredFlatJoinTablesWhenScanningIsDisabled() {
        var joined = joinedConnector("""
                objectClass('FlatTargeted') { sql {
                    table '%s'
                    join { table '%s'; prefixAttributes 'profile_' }
                } }
                """.formatted(objectClass(USER).getObjectClassValue(), objectClass(PROFILE).getObjectClassValue()), false);
        try {
            assertThat(search(joined, new ObjectClass("FlatTargeted"), null)).hasSize(2);
            assertThat(joined.context().getTableInfos()).hasSize(2);
        } finally {
            joined.dispose();
        }
    }

    private static final class JoinedConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        private final String script;
        private final boolean onlyExplicitlyListed;

        private JoinedConnector(String script, boolean onlyExplicitlyListed) {
            super(false);
            this.script = script;
            this.onlyExplicitlyListed = onlyExplicitlyListed;
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) { }

        @Override
        protected void initializeSchema(SqlSchemaBuilder builder) { builder.onlyExplicitlyListed(onlyExplicitlyListed); }

        @Override
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) { loader.load(script); }
    }

    @Test
    public final void searchesByUidAndAttributeAcrossDatabase() {
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var users = search(userClass, null);

        assertThat(users).hasSize(2);
        var alice = users.stream()
                .filter(object -> "alice".equals(value(object, username)))
                .findFirst()
                .orElseThrow();

        assertThat(search(userClass, FilterBuilder.equalTo(
                AttributeBuilder.build(Uid.NAME, alice.getUid().getUidValue()))))
                .extracting(ConnectorObject::getUid)
                .extracting(Uid::getUidValue)
                .containsExactly(alice.getUid().getUidValue());
        assertThat(search(userClass, FilterBuilder.equalTo(
                AttributeBuilder.build(username, "alice"))))
                .hasSize(1)
                .first()
                .extracting(object -> value(object, username))
                .isEqualTo("alice");
    }

    @Test
    public final void createsUpdatesAndDeletesGeneratedUid() {
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var email = attributeName(USER, "email");
        var active = attributeName(USER, "active");
        var quota = attributeName(USER, "quota");

        var uid = connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-created-user"),
                AttributeBuilder.build(username, "charlie"),
                AttributeBuilder.build(email, "charlie@example.com"),
                AttributeBuilder.build(active, mappedNumber(USER, "active", "1")),
                AttributeBuilder.build(quota, mappedNumber(USER, "quota", "30.25"))), OPTIONS);

        assertThat(uid.getUidValue()).isNotBlank();
        assertThat(value(get(userClass, uid), username)).isEqualTo("charlie");

        connector.updateDelta(userClass, uid, Set.of(
                AttributeDeltaBuilder.build(email, List.of("changed@example.com"))), OPTIONS);
        assertThat(value(get(userClass, uid), email)).isEqualTo("changed@example.com");

        connector.updateDelta(userClass, uid, Set.of(
                AttributeDeltaBuilder.build(email, List.of())), OPTIONS);
        assertThat(value(get(userClass, uid), email)).isNull();

        connector.delete(userClass, uid, OPTIONS);
        assertThat(search(userClass, uidFilter(uid))).isEmpty();
    }

    @Test
    public final void createsUpdatesAndDeletesChildTableAttributes() {
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var profile = attributeName(USER, PROFILE);
        var emails = attributeName(USER, EMAILS);
        var phones = attributeName(USER, PHONES);
        var bio = attributeName(PROFILE, "bio");
        var phoneNumber = attributeName(PHONES, "phone_number");
        var phoneType = attributeName(PHONES, "phone_type");
        var priority = attributeName(PHONES, "priority");

        var uid = connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-child-user"),
                AttributeBuilder.build(username, "child-user"),
                AttributeBuilder.build(profile,
                        embedded(PROFILE, AttributeBuilder.build(bio, "Original bio"))),
                AttributeBuilder.build(emails,
                        List.of("first@example.com", "keep@example.com")),
                AttributeBuilder.build(phones, List.of(
                        embedded(PHONES,
                                AttributeBuilder.build(phoneNumber, "555-1001"),
                                AttributeBuilder.build(phoneType, "mobile"),
                                AttributeBuilder.build(priority, mappedNumber(PHONES, "priority", "1"))),
                        embedded(PHONES,
                                AttributeBuilder.build(phoneNumber, "555-1002"),
                                AttributeBuilder.build(phoneType, "home"),
                                AttributeBuilder.build(priority, mappedNumber(PHONES, "priority", "2")))))), OPTIONS);

        var created = get(userClass, uid);
        assertThat(values(created, emails))
                .containsExactlyInAnyOrder("first@example.com", "keep@example.com");
        assertThat(embeddedValue(onlyEmbedded(created, profile), "bio"))
                .isEqualTo("Original bio");
        assertThat(embeddedValues(created, phones).stream()
                .map(phone -> embeddedValue(phone, "phone_number")))
                .containsExactlyInAnyOrder("555-1001", "555-1002");
        assertThat(embeddedValues(created, phones).stream()
                .map(phone -> ((Number) embeddedValue(phone, "priority")).intValue()))
                .containsExactlyInAnyOrder(1, 2);

        connector.updateDelta(userClass, uid, Set.of(
                AttributeDeltaBuilder.build(profile, List.of(
                        embedded(PROFILE, AttributeBuilder.build(bio, "Updated bio")))),
                AttributeDeltaBuilder.build(phones, List.of(
                        embedded(PHONES,
                                AttributeBuilder.build(phoneNumber, "555-1003"),
                                AttributeBuilder.build(phoneType, "work"),
                                AttributeBuilder.build(priority, mappedNumber(PHONES, "priority", "3")))))), OPTIONS);
        connector.updateDelta(userClass, uid, Set.of(new AttributeDeltaBuilder()
                .setName(emails)
                .addValueToRemove("first@example.com")
                .addValueToAdd("new@example.com")
                .build()), OPTIONS);
        connector.updateDelta(userClass, uid, Set.of(new AttributeDeltaBuilder()
                .setName(phones)
                .addValueToRemove(embedded(PHONES,
                        AttributeBuilder.build(phoneNumber, "555-1003")))
                .addValueToAdd(embedded(PHONES,
                        AttributeBuilder.build(phoneNumber, "555-1004"),
                        AttributeBuilder.build(phoneType, "other"),
                        AttributeBuilder.build(priority, mappedNumber(PHONES, "priority", "4"))))
                .build()), OPTIONS);

        var updated = get(userClass, uid);
        assertThat(embeddedValue(onlyEmbedded(updated, profile), "bio"))
                .isEqualTo("Updated bio");
        assertThat(values(updated, emails))
                .containsExactlyInAnyOrder("keep@example.com", "new@example.com");
        assertThat(embeddedValues(updated, phones).stream()
                .map(phone -> embeddedValue(phone, "phone_number")))
                .containsExactly("555-1004");
        assertThat(((Number) embeddedValue(onlyEmbedded(updated, phones), "priority")).intValue())
                .isEqualTo(4);

        connector.delete(userClass, uid, OPTIONS);
        assertThat(search(userClass, uidFilter(uid))).isEmpty();
    }

    @Test
    public final void rollsBackParentWhenChildCreateFails() {
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var phones = attributeName(USER, PHONES);
        var phoneType = attributeName(PHONES, "phone_type");

        assertThatThrownBy(() -> connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-broken-child"),
                AttributeBuilder.build(username, "broken-child"),
                AttributeBuilder.build(phones,
                        embedded(PHONES, AttributeBuilder.build(phoneType, "invalid")))), OPTIONS))
                .isInstanceOf(ConnectorException.class);

        assertThat(search(userClass, FilterBuilder.equalTo(
                AttributeBuilder.build(username, "broken-child"))))
                .isEmpty();
    }

    @Test
    public final void rollsBackChildReplacementWhenInsertFails() {
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var phones = attributeName(USER, PHONES);
        var phoneNumber = attributeName(PHONES, "phone_number");
        var phoneType = attributeName(PHONES, "phone_type");

        var uid = connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-rollback-child"),
                AttributeBuilder.build(username, "rollback-child"),
                AttributeBuilder.build(phones,
                        embedded(PHONES,
                                AttributeBuilder.build(phoneNumber, "555-2001"),
                                AttributeBuilder.build(phoneType, "mobile")))), OPTIONS);

        assertThatThrownBy(() -> connector.updateDelta(userClass, uid, Set.of(
                AttributeDeltaBuilder.build(username, List.of("should-rollback")),
                AttributeDeltaBuilder.build(phones, List.of(
                        embedded(PHONES, AttributeBuilder.build(phoneType, "invalid"))))), OPTIONS))
                .isInstanceOf(ConnectorException.class);

        var afterFailedUpdate = get(userClass, uid);
        assertThat(value(afterFailedUpdate, username)).isEqualTo("rollback-child");
        assertThat(embeddedValues(afterFailedUpdate, phones).stream()
                .map(phone -> embeddedValue(phone, "phone_number")))
                .containsExactly("555-2001");
    }

    @Test
    public final void clearsAllRelatedAttributeShapes() {
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var profile = attributeName(USER, PROFILE);
        var emails = attributeName(USER, EMAILS);
        var phones = attributeName(USER, PHONES);
        var bio = attributeName(PROFILE, "bio");
        var phoneNumber = attributeName(PHONES, "phone_number");

        var uid = connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-clear-related"),
                AttributeBuilder.build(username, "clear-related"),
                AttributeBuilder.build(profile,
                        embedded(PROFILE, AttributeBuilder.build(bio, "Temporary bio"))),
                AttributeBuilder.build(emails, "temporary@example.com"),
                AttributeBuilder.build(phones,
                        embedded(PHONES,
                                AttributeBuilder.build(phoneNumber, "555-3001")))), OPTIONS);

        connector.updateDelta(userClass, uid, Set.of(
                AttributeDeltaBuilder.build(profile, List.of()),
                AttributeDeltaBuilder.build(emails, List.of()),
                AttributeDeltaBuilder.build(phones, List.of())), OPTIONS);

        var cleared = get(userClass, uid);
        assertThat(values(cleared, profile)).isEmpty();
        assertThat(values(cleared, emails)).isEmpty();
        assertThat(values(cleared, phones)).isEmpty();
    }

    @Test
    public final void rejectsMultipleValuesForSingleValuedRelatedAttribute() {
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var profile = attributeName(USER, PROFILE);
        var bio = attributeName(PROFILE, "bio");

        assertThatThrownBy(() -> connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-invalid-profile"),
                AttributeBuilder.build(username, "invalid-profile"),
                AttributeBuilder.build(profile, List.of(
                        embedded(PROFILE, AttributeBuilder.build(bio, "First")),
                        embedded(PROFILE, AttributeBuilder.build(bio, "Second"))))), OPTIONS))
                .isInstanceOf(InvalidAttributeValueException.class);

        assertThat(search(userClass, FilterBuilder.equalTo(
                AttributeBuilder.build(username, "invalid-profile"))))
                .isEmpty();
    }

    @Test
    public final void rejectsUnknownEmbeddedColumnsAndRollsBackCreate() {
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var phones = attributeName(USER, PHONES);

        assertThatThrownBy(() -> connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-unknown-child-column"),
                AttributeBuilder.build(username, "unknown-child-column"),
                AttributeBuilder.build(phones,
                        embedded(PHONES,
                                AttributeBuilder.build("does_not_exist", "invalid")))), OPTIONS))
                .isInstanceOf(InvalidAttributeValueException.class);

        assertThat(search(userClass, FilterBuilder.equalTo(
                AttributeBuilder.build(username, "unknown-child-column"))))
                .isEmpty();
    }

    @Test
    public final void addingAnExistingRelatedValueIsIdempotent() {
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var emails = attributeName(USER, EMAILS);

        var uid = connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-idempotent-related"),
                AttributeBuilder.build(username, "idempotent-related"),
                AttributeBuilder.build(emails, "existing@example.com")), OPTIONS);

        connector.updateDelta(userClass, uid, Set.of(
                AttributeDeltaBuilder.build(username, List.of("idempotent-related-updated")),
                new AttributeDeltaBuilder()
                        .setName(emails)
                        .addValueToAdd("existing@example.com")
                        .build()), OPTIONS);

        var updated = get(userClass, uid);
        assertThat(value(updated, username)).isEqualTo("idempotent-related-updated");
        assertThat(values(updated, emails)).containsExactly("existing@example.com");
    }

    @Test
    public final void supportsNonUidRelatedTableJoinsWhenReportedByJdbc() {
        if (!database.capabilities().supportsNonPrimaryForeignKeyMetadata()) {
            return;
        }
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var aliases = attributeName(USER, USER_ALIASES);
        var userUid = connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-non-uid-join"),
                AttributeBuilder.build(username, "non-uid-parent-key"),
                AttributeBuilder.build(aliases, List.of("first-alias", "second-alias"))), OPTIONS);

        assertThat(values(get(userClass, userUid), aliases))
                .containsExactlyInAnyOrder("first-alias", "second-alias");
        connector.updateDelta(userClass, userUid, Set.of(new AttributeDeltaBuilder()
                .setName(aliases)
                .addValueToRemove("first-alias")
                .addValueToAdd("third-alias")
                .build()), OPTIONS);
        assertThat(values(get(userClass, userUid), aliases))
                .containsExactlyInAnyOrder("second-alias", "third-alias");
        connector.delete(userClass, userUid, OPTIONS);
    }

    @Test
    public final void supportsCompositeRelatedTableJoins() {
        var compositeClass = objectClass(COMPOSITE);
        var roleName = attributeName(COMPOSITE, "role_name");
        var tags = attributeName(COMPOSITE, COMPOSITE_TAGS);
        var compositeUid = connector.create(compositeClass, Set.of(
                AttributeBuilder.build(Uid.NAME, "3.20"),
                AttributeBuilder.build(Name.NAME, "3.20"),
                AttributeBuilder.build(roleName, "composite-child"),
                AttributeBuilder.build(tags, List.of("first-tag", "second-tag"))), OPTIONS);

        assertThat(values(get(compositeClass, compositeUid), tags))
                .containsExactlyInAnyOrder("first-tag", "second-tag");
        connector.updateDelta(compositeClass, compositeUid, Set.of(new AttributeDeltaBuilder()
                .setName(tags)
                .addValueToRemove("first-tag")
                .addValueToAdd("third-tag")
                .build()), OPTIONS);
        assertThat(values(get(compositeClass, compositeUid), tags))
                .containsExactlyInAnyOrder("second-tag", "third-tag");
        connector.delete(compositeClass, compositeUid, OPTIONS);
        assertThat(search(compositeClass, uidFilter(compositeUid))).isEmpty();
    }

    @Test
    public final void resolvesJunctionTableReferences() {
        var alice = search(objectClass(USER), FilterBuilder.equalTo(
                AttributeBuilder.build(attributeName(USER, "username"), "alice")))
                .getFirst();
        var developers = search(objectClass(GROUP), FilterBuilder.equalTo(
                AttributeBuilder.build(attributeName(GROUP, "name"), "developers")))
                .getFirst();

        var reference = (ConnectorObjectReference) values(
                alice, attributeName(USER, GROUP)).getFirst();
        assertThat(reference.getValue().getObjectClass().getObjectClassValue())
                .isEqualToIgnoringCase(objectClass(GROUP).getObjectClassValue());
        assertThat(AttributeUtil.getUidAttribute(reference.getValue().getAttributes()).getUidValue())
                .isEqualTo(developers.getUid().getUidValue());
    }

    @Test
    public final void supportsDatabaseDefaultsAndConstraintErrors() {
        var userClass = objectClass(USER);
        var username = attributeName(USER, "username");
        var email = attributeName(USER, "email");

        var defaultedUid = connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-default-user"),
                AttributeBuilder.build(email)), OPTIONS);
        assertThat(value(get(userClass, defaultedUid), username)).isEqualTo("anonymous");

        assertThatThrownBy(() -> connector.create(userClass, Set.of(
                AttributeBuilder.build(Name.NAME, "contract-duplicate-user"),
                AttributeBuilder.build(username, "alice")), OPTIONS))
                .isInstanceOf(AlreadyExistsException.class);

    }

    @Test
    public final void supportsNaturalAndCompositeUids() {
        var external = objectClass(EXTERNAL);
        var displayName = attributeName(EXTERNAL, "display_name");
        var externalUid = connector.create(external, Set.of(
                AttributeBuilder.build(Uid.NAME, "external-100"),
                AttributeBuilder.build(Name.NAME, "external-100"),
                AttributeBuilder.build(displayName, "External 100")), OPTIONS);

        assertThat(externalUid.getUidValue()).isEqualTo("external-100");
        assertThat(value(get(external, externalUid), displayName)).isEqualTo("External 100");
        connector.delete(external, externalUid, OPTIONS);

        var composite = objectClass(COMPOSITE);
        var roleName = attributeName(COMPOSITE, "role_name");
        var compositeUid = connector.create(composite, Set.of(
                AttributeBuilder.build(Uid.NAME, "2.10"),
                AttributeBuilder.build(Name.NAME, "2.10"),
                AttributeBuilder.build(roleName, "member")), OPTIONS);

        assertThat(compositeUid.getUidValue()).isEqualTo("2.10");
        assertThat(value(get(composite, compositeUid), roleName)).isEqualTo("member");
        connector.delete(composite, compositeUid, OPTIONS);
        assertThat(search(composite, uidFilter(compositeUid))).isEmpty();
    }

    @Test
    public final void exportsStructuredDevelopmentMetadata() throws Exception {
        assertThat(schemaNames()).contains(SqlDevelopmentMode.TABLE_OC_NAME);

        var tables = search(new ObjectClass(SqlDevelopmentMode.TABLE_OC_NAME), null);
        var user = tableNamed(tables, USER);
        var address = tableNamed(tables, ADDRESS);
        var view = tableNamed(tables, USER_VIEW);

        assertThat(attributeValue(user, SqlDevelopmentMode.TABLE_TYPE_ATTRIBUTE).toString())
                .containsIgnoringCase("TABLE");
        if (database.capabilities().supportsNativeDefinitions()) {
            assertThat((String) attributeValue(user, SqlDevelopmentMode.DEFINITION_ATTRIBUTE))
                    .isNotBlank()
                    .containsIgnoringCase(USER);
            assertThat((String) attributeValue(view, SqlDevelopmentMode.DEFINITION_ATTRIBUTE))
                    .isNotBlank()
                    .containsIgnoringCase(USER_VIEW)
                    .containsIgnoringCase(USER);
        } else {
            assertThat(user.getAttributeByName(SqlDevelopmentMode.DEFINITION_ATTRIBUTE)).isNull();
            assertThat(view.getAttributeByName(SqlDevelopmentMode.DEFINITION_ATTRIBUTE)).isNull();
        }

        var userContent = json(user);
        var id = column(userContent, "id");
        var username = column(userContent, "username");
        assertThat(id.get("primaryKey")).isEqualTo(true);
        assertThat(id.get("autoIncrement")).isEqualTo(true);
        assertThat(username.get("nullable")).isEqualTo(false);
        if (database.capabilities().supportsJdbcDefaults()) {
            assertThat(String.valueOf(username.get("defaultValue"))).contains("anonymous");
        } else {
            assertThat(username.get("defaultValue")).isNull();
        }

        var addressContent = json(address);
        var userId = column(addressContent, "user_id");
        assertThat(String.valueOf(userId.get("referencedTable"))).isEqualToIgnoringCase(USER);
        assertThat(String.valueOf(userId.get("referencedColumn"))).isEqualToIgnoringCase("id");
        assertThat(String.valueOf(userId.get("foreignKeyName")))
                .containsIgnoringCase("contract_address_user");

        if (database.capabilities().supportsRemarks()) {
            assertThat(attributeValue(user, SqlDevelopmentMode.REMARKS_ATTRIBUTE))
                    .isEqualTo("Contract users");
            assertThat(username.get("remarks")).isEqualTo("Contract login name");
        }
    }

    @Test
    public final void filtersMetadataAndHidesItOutsideDevelopmentMode() throws Exception {
        var metadataClass = new ObjectClass(SqlDevelopmentMode.TABLE_OC_NAME);
        var user = tableNamed(search(metadataClass, null), USER);

        assertThat(search(metadataClass, FilterBuilder.equalTo(
                AttributeBuilder.build(Uid.NAME, user.getUid().getUidValue()))))
                .extracting(object -> object.getUid().getUidValue())
                .containsExactly(user.getUid().getUidValue());
        assertThat(search(metadataClass, FilterBuilder.equalTo(
                AttributeBuilder.build(Name.NAME, user.getName().getNameValue()))))
                .hasSize(1);

        var nonDevelopmentConnector = new ContractConnector();
        nonDevelopmentConnector.init(database.configuration(false));
        try {
            assertThat(nonDevelopmentConnector.schema().getObjectClassInfo().stream()
                    .map(ObjectClassInfo::getType))
                    .doesNotContain(SqlDevelopmentMode.TABLE_OC_NAME);
        } finally {
            nonDevelopmentConnector.dispose();
        }
    }

    @Test
    public final void rejectsWritesToViews() {
        var view = objectClass(USER_VIEW);
        assertThatThrownBy(() -> connector.create(view, Set.of(
                AttributeBuilder.build(Name.NAME, "read-only")), OPTIONS))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private SqlTestDatabase initialize(SqlTestDatabase candidate) throws Exception {
        candidate.initializeSchema();
        return candidate;
    }

    private List<String> schemaNames() {
        return connector.schema().getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType)
                .toList();
    }

    private ObjectClassInfo objectClassInfo(String expectedName) {
        return connector.schema().getObjectClassInfo().stream()
                .filter(info -> info.getType().equalsIgnoreCase(expectedName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Object class not found: " + expectedName));
    }

    private ObjectClass objectClass(String expectedName) {
        return new ObjectClass(objectClassInfo(expectedName).getType());
    }

    private AttributeInfo attributeInfo(ObjectClassInfo objectClass, String expectedName) {
        return objectClass.getAttributeInfo().stream()
                .filter(attribute -> expectedName.equalsIgnoreCase(attribute.getName())
                        || expectedName.equalsIgnoreCase(attribute.getNativeName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Attribute not found: " + objectClass.getType() + "." + expectedName
                                + "; available: " + objectClass.getAttributeInfo().stream()
                                        .map(AttributeInfo::getName)
                                        .toList()));
    }

    private String attributeName(String objectClass, String expectedName) {
        return attributeInfo(objectClassInfo(objectClass), expectedName).getName();
    }

    private Object mappedNumber(String objectClass, String attribute, String value) {
        var type = attributeInfo(objectClassInfo(objectClass), attribute).getType();
        if (type == Boolean.class || type == boolean.class) {
            return !"0".equals(value);
        }
        if (type == Integer.class || type == int.class) {
            return Integer.valueOf(value.split("\\.")[0]);
        }
        if (type == Long.class || type == long.class) {
            return Long.valueOf(value.split("\\.")[0]);
        }
        if (type == Float.class || type == float.class) {
            return Float.valueOf(value);
        }
        if (type == Double.class || type == double.class) {
            return Double.valueOf(value);
        }
        return new BigDecimal(value);
    }

    private List<ConnectorObject> search(ObjectClass objectClass, Filter filter) {
        var result = new ArrayList<ConnectorObject>();
        connector.executeQuery(objectClass, filter, result::add, OPTIONS);
        return result;
    }

    private ConnectorObject get(ObjectClass objectClass, Uid uid) {
        var result = search(objectClass, uidFilter(uid));
        assertThat(result).hasSize(1);
        return result.getFirst();
    }

    private static Filter uidFilter(Uid uid) {
        return FilterBuilder.equalTo(AttributeBuilder.build(Uid.NAME, uid.getUidValue()));
    }

    private static Object value(ConnectorObject object, String name) {
        var attribute = object.getAttributeByName(name);
        return attribute == null ? null : AttributeUtil.getSingleValue(attribute);
    }

    private static List<Object> values(ConnectorObject object, String name) {
        var attribute = object.getAttributeByName(name);
        return attribute == null || attribute.getValue() == null
                ? List.of()
                : attribute.getValue();
    }

    private EmbeddedObject embedded(String objectClass, Attribute... attributes) {
        return new EmbeddedObject(objectClass(objectClass), Set.of(attributes));
    }

    private static EmbeddedObject onlyEmbedded(ConnectorObject object, String attributeName) {
        assertThat(values(object, attributeName)).hasSize(1);
        return (EmbeddedObject) values(object, attributeName).getFirst();
    }

    private static List<EmbeddedObject> embeddedValues(
            ConnectorObject object, String attributeName) {
        return values(object, attributeName).stream()
                .map(EmbeddedObject.class::cast)
                .toList();
    }

    private static Object embeddedValue(EmbeddedObject object, String expectedName) {
        var attribute = object.getAttributes().stream()
                .filter(candidate -> candidate.getName().equalsIgnoreCase(expectedName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Embedded attribute not found: " + expectedName));
        return AttributeUtil.getSingleValue(attribute);
    }

    private static ConnectorObject tableNamed(List<ConnectorObject> tables, String name) {
        return tables.stream()
                .filter(table -> table.getName().getNameValue().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Table metadata not found: " + name));
    }

    private static Object attributeValue(ConnectorObject object, String name) {
        return AttributeUtil.getSingleValue(object.getAttributeByName(name));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(ConnectorObject object) throws Exception {
        return JSON.readValue(
                (String) attributeValue(object, SqlDevelopmentMode.TABLE_CONTENT_ATTRIBUTE), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> column(Map<String, Object> table, String name) {
        var columns = (List<Map<String, Object>>) table.get("columns");
        return columns.stream()
                .filter(column -> name.equalsIgnoreCase(String.valueOf(column.get("name"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Column metadata not found: " + name));
    }

    private static void closeQuietly(SqlTestDatabase database) {
        try {
            database.close();
        } catch (Exception ignored) {
            // Keep the original setup failure.
        }
    }

    private static final class ContractConnector
            extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {

        private ContractConnector() {
            super(false);
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) {
        }

        @Override
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) {
        }
    }
}
