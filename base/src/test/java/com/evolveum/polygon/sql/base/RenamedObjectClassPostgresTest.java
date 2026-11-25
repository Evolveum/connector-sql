/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.test.PostgresDatabaseInitializer;
import com.evolveum.polygon.sql.base.test.TestConnectors;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectReference;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests for WP 13580: when the object class for a table is explicitly named
 * differently (the ticket renames {@code crew_member} to {@code CrewMember}), every generated
 * cross-reference must point at the declared object class name — not at the raw detected table
 * name. Stale table-name references made midPoint reject the resource schema with
 * {@code No object class definition for 'crew_member'} (HTTP 500) and the connector NPE in
 * {@code SqlWriteHandlers} when an operation script still referenced the detected name.
 *
 * <p>Runs against embedded PostgreSQL 16 (zonky) using the exact schema, seed data, and
 * connector script from the work package ({@code postgresql/renamed-oc/schema.sql} and the
 * {@code CrewMember} schema script below).
 */
@Test(singleThreaded = true)
public class RenamedObjectClassPostgresTest {

    /** The ticket's object class script: the OC is declared as {@code CrewMember} over table {@code crew_member}. */
    private static final String CREW_MEMBER_SCHEMA_SCRIPT = """
            objectClass("CrewMember") {
                description "A crew member"
                sql {
                    table "crew_member"
                    schema "amc"

                    join {
                        table "clearance_level"
                        prefixAttributes "clearance_"
                        skipAttributes "clearance_id", "code", "rank"
                    }
                    join {
                        table "department"
                        prefixAttributes "department_"
                        skipAttributes "department_id", "code", "active", "created_at"
                    }
                }

                onlyExplicitlyListed(true)

                attribute("crew_member_id") {
                    connId { name UID }
                    sql {
                        type BIGINT
                        primaryKey
                        autoIncrement
                    }
                }

                attribute("external_uid") {
                    connId { name "externalUid" }
                    sql { type VARCHAR(64); unique; notNull true }
                }

                attribute("given_name") {
                    connId { name "givenName" }
                    sql { type VARCHAR(80); notNull true }
                }

                attribute("family_name") {
                    connId { name "familyName" }
                    sql { type VARCHAR(80); notNull true }
                }

                attribute("display_name") {
                    connId { name NAME }
                    sql { type VARCHAR(160); notNull true }
                }

                attribute("email") {
                    connId { name "email" }
                    sql { type VARCHAR(255); unique }
                }

                attribute("employee_number") {
                    connId { name "employeeNumber" }
                    sql { type VARCHAR(20); unique }
                }

                attribute("status") {
                    connId { name "status" }
                    sql { type VARCHAR(20); notNull true }
                }

                attribute("clearance_id") {
                    connId { name "clearanceId" }
                    sql { type SMALLINT }
                }

                attribute("department_id") {
                    connId { name "departmentId" }
                    sql { type INTEGER }
                }

                attribute("home_station") {
                    connId { name "homeStation" }
                    sql { type VARCHAR(50) }
                }

                attribute("emergency_contact") {
                    connId { name "emergencyContact" }
                    sql { type VARCHAR(255) }
                }

                attribute("hire_date") {
                    connId { name "hireDate" }
                    sql { type DATE }
                }

                attribute("termination_date") {
                    connId { name "terminationDate" }
                    sql { type DATE }
                }

                attribute("created_at") {
                    connId { name "createdAt" }
                }

                attribute("updated_at") {
                    connId { name "updatedAt" }
                }

                attribute("version") {
                    connId { name "version" }
                    sql { type INTEGER; notNull true }
                }

                attribute("clearance_name") {
                    connId { name "clearanceName" }
                }

                attribute("clearance_description") {
                    connId { name "clearanceDescription" }
                }

                attribute("department_name") {
                    connId { name "departmentName" }
                }

                attribute("department_station") {
                    connId { name "departmentStation" }
                }
            }
            """;

    /**
     * Operation script the way the wizard generates it for a renamed object class: the document
     * is keyed by the <em>detected table name</em> ({@code crew_member}), while the schema
     * declares {@code CrewMember}. Must resolve to the declared object class (WP 13580 NPE).
     */
    private static final String OPERATION_SCRIPT_BY_TABLE_NAME = """
            objectClasses:
              crew_member:
                search:
                  enabled: true
            """;

    private static final String OPERATION_SCRIPT_BY_DECLARED_NAME = """
            objectClasses:
              CrewMember:
                search:
                  enabled: true
            """;

    private static final String OPERATION_SCRIPT_UNKNOWN_CLASS = """
            objectClasses:
              bogus_object_class:
                search:
                  enabled: true
            """;

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
            executeSql(conn.getConnection(), "postgresql/renamed-oc/schema.sql");
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
    public void renamedObjectClassSchemaReferencesResolveToDeclaredName() throws Exception {
        var schema = buildConnIdSchema(CREW_MEMBER_SCHEMA_SCRIPT);

        // The ticket's exact failure mode: midPoint's ResourceSchemaParser#parseNativeReferenceTypes
        // requires every reference attribute's referencedObjectClass to exist in the schema.
        assertAllReferenceTypesResolve(schema);
        // The reference attributes on the junction still carry the midPoint-required role.
        assertMidPointAcceptableReferenceRoles(schema);

        // The junction reference attribute keeps the table name as its attribute name ...
        var reference = referenceAttribute(schema, "crew_role", "crew_member");
        assertThat(reference.getType()).isEqualTo(ConnectorObjectReference.class);
        assertThat(reference.isMultiValued()).isTrue();
        assertThat(reference.getRoleInReference())
                .isEqualTo(AttributeInfo.RoleInReference.SUBJECT.toString());
        // ... but the referenced object class is the declared name, not the detected table name.
        assertThat(reference.getReferencedObjectClassName()).isEqualTo("CrewMember");

        // The renamed object class itself carries the declared name in the schema.
        assertThat(findObjectClass(schema, "CrewMember").getAttributeInfo())
                .extracting(AttributeInfo::getName)
                .contains("__UID__", "clearanceName", "departmentName");
        // Explicit flat joins replace automatic junction-reference projection on that object
        // (ChildTableRelationshipDetectionRule), so the renamed OC has no junction reference.
        assertThat(findObjectClass(schema, "CrewMember").getAttributeInfo())
                .extracting(AttributeInfo::getName)
                .doesNotContain("crew_role");
    }

    @Test
    public void fullConnectorSearchReturnsDeclaredObjectClassInReferences() throws Exception {
        var connector = newConnector(OPERATION_SCRIPT_BY_TABLE_NAME);
        try {
            var schema = connector.schema();
            assertAllReferenceTypesResolve(schema);
            assertMidPointAcceptableReferenceRoles(schema);

            // The operation script keyed by the detected table name must have landed on the
            // declared object class's handler (previously: NPE during connector initialization).
            var handlers = connector.context().handlers();
            assertThat(handlers).containsKey(new ObjectClass("CrewMember"));
            assertThat(handlers).doesNotContainKey(new ObjectClass("crew_member"));

            // Searching the other junction side resolves references stamped with the declared name.
            var roles = search(connector, new ObjectClass("crew_role"));
            assertThat(roles).hasSize(6);
            var referenceValues = roles.stream()
                    .flatMap(obj -> values(obj, "crew_member").stream())
                    .map(ConnectorObjectReference.class::cast)
                    .toList();
            assertThat(referenceValues).isNotEmpty();
            assertThat(referenceValues).allSatisfy(reference ->
                    assertThat(reference.getValue().getObjectClass())
                            .isEqualTo(new ObjectClass("CrewMember")));
            // role-2004 (crew_role_id 4, 'Station Security') is assigned to crew-1001 only.
            var stationSecurity = byUid(roles, "4");
            assertThat(referenceUids(stationSecurity, "crew_member")).containsExactly("1");

            // The renamed object class itself is searchable, including its joined attributes.
            var members = search(connector, new ObjectClass("CrewMember"));
            assertThat(members).hasSize(6);
            var mira = byUid(members, "1");
            assertThat(value(mira, "clearanceName")).isEqualTo("Command Access");
            assertThat(value(mira, "departmentName")).isEqualTo("Security");
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void operationScriptKeyedByDeclaredNameStillResolves() throws Exception {
        var connector = newConnector(OPERATION_SCRIPT_BY_DECLARED_NAME);
        try {
            connector.schema();
            var handlers = connector.context().handlers();
            assertThat(handlers).containsKey(new ObjectClass("CrewMember"));
        } finally {
            connector.dispose();
        }
    }

    @Test
    public void operationScriptWithUnknownObjectClassFailsWithClearError() throws Exception {
        var connector = newConnector(OPERATION_SCRIPT_UNKNOWN_CLASS);
        try {
            // Pre-fix this was a NullPointerException deep in SqlWriteHandlers; the error must
            // name the unknown object class and the known ones.
            assertThatThrownBy(connector::schema)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No object class definition for 'bogus_object_class'")
                    .hasMessageContaining("CrewMember");
        } finally {
            connector.dispose();
        }
    }

    private TestConnectors.DefaultTestConnector newConnector(String operationScript) throws Exception {
        var connector = TestConnectors.of(
                (GroovyScriptLoader handlerLoader) ->
                        ((SqlHandlerLoader) handlerLoader).loadFromString(
                                "/CrewMember.search.all.op.yaml", operationScript),
                (GroovySchemaLoader schemaLoader) -> schemaLoader.load(CREW_MEMBER_SCHEMA_SCRIPT));

        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setScanTables(true);
        config.setScanViews(true);

        connector.init(config);
        return connector;
    }

    /**
     * Replicates midPoint's {@code ResourceSchemaParser#parseNativeReferenceTypes} /
     * {@code NativelyProvidedShadowReferenceTypeDefinition#resolveObjectClass}: every reference
     * attribute must reference an object class present in the schema, otherwise midPoint fails
     * resource schema processing with "No object class definition for ...".
     */
    private static void assertAllReferenceTypesResolve(Schema schema) {
        var classNames = schema.getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType)
                .toList();
        for (var oc : schema.getObjectClassInfo()) {
            for (var attr : oc.getAttributeInfo()) {
                if (!attr.isReference()) {
                    continue;
                }
                var referenced = attr.getReferencedObjectClassName();
                assertThat(referenced)
                        .describedAs("Reference attribute <%s> of object class '%s' has no referencedObjectClass",
                                attr, oc.getType())
                        .isNotNull();
                assertThat(classNames)
                        .describedAs("midPoint fails resource schema processing: No object class "
                                + "definition for '%s' (in %s)", referenced, classNames)
                        .anySatisfy(existing -> assertThat(existing).isEqualToIgnoringCase(referenced));
            }
        }
    }

    /**
     * Replicates midPoint's {@code ConnIdSchemaParser#determineParticipantRole}: every reference
     * attribute must carry a role of {@code __SUBJECT__} or {@code __OBJECT__}.
     */
    private static void assertMidPointAcceptableReferenceRoles(Schema schema) {
        for (var oc : schema.getObjectClassInfo()) {
            for (var attr : oc.getAttributeInfo()) {
                if (!attr.isReference()) {
                    continue;
                }
                assertThat(attr.getRoleInReference())
                        .describedAs("Missing role in reference in <%s> of object class '%s'",
                                attr, oc.getType())
                        .isIn(
                                AttributeInfo.RoleInReference.SUBJECT.toString(),
                                AttributeInfo.RoleInReference.OBJECT.toString());
            }
        }
    }

    private static AttributeInfo referenceAttribute(Schema schema, String objectClass, String attribute) {
        var oc = findObjectClass(schema, objectClass);
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
                        "Object class '%s' not found in schema; present: %s".formatted(name,
                                schema.getObjectClassInfo().stream()
                                        .map(ObjectClassInfo::getType)
                                        .toList())));
    }

    private static List<ConnectorObject> search(AbstractGroovySqlConnector connector, ObjectClass objectClass)
            throws Exception {
        var results = new ArrayList<ConnectorObject>();
        connector.executeQuery(objectClass, null, results::add, new OperationOptions(Map.of()));
        return results;
    }

    private static ConnectorObject byUid(List<ConnectorObject> objects, String uid) {
        return objects.stream()
                .filter(object -> uid.equals(object.getUid().getUidValue()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Object with UID %s not found; got: %s"
                        .formatted(uid, objects.stream().map(o -> o.getUid().getUidValue()).toList())));
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

    private static List<String> referenceUids(ConnectorObject object, String name) {
        return values(object, name).stream()
                .map(ConnectorObjectReference.class::cast)
                .map(reference -> AttributeUtil.getUidAttribute(
                        reference.getValue().getAttributes()).getUidValue())
                .toList();
    }

    private Schema buildConnIdSchema(String schemaScript) throws Exception {
        List<SqlTableInfo> tables = new SqlSchemaDetector(context).discover();

        var builder = new SqlSchemaBuilderImpl(StubConnector.class, context);
        new GroovySchemaLoader(context.configuration().groovyContext(), builder)
                .load(schemaScript);

        var translator = new SqlSchemaTranslator(builder, tables);
        translator.connector(StubConnector.class, context);
        translator.translate(List.of());
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
}
