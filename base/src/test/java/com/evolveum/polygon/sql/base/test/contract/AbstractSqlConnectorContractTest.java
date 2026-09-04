/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.test.contract;

import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.dev.SqlDevelopmentMode;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
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
