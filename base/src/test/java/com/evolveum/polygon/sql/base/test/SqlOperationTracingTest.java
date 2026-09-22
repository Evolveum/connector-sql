/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.test;

import com.evolveum.polygon.conndev.devtools.log.ConndevLogFormat;
import com.evolveum.polygon.conndev.devtools.log.OperationLogParser;
import com.evolveum.polygon.conndev.devtools.log.OperationTrace;
import com.evolveum.polygon.conndev.devtools.log.ProtocolPayload;
import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.sql.base.test.contract.SqlTestDatabases;
import com.evolveum.polygon.sql.base.test.contract.SqlTestDatabase;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDeltaBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip verification of the structured operation tracing for ConnId operations: each
 * operation is wrapped in an operation entry ({@code conndev-log/v1}) and the SQL executed by the
 * operation handlers is attached to it as a {@code sql} protocol event with the SQL text and the
 * bound parameter values. The captured lines are re-correlated with the devtools
 * {@link OperationLogParser}.
 */
@Test(singleThreaded = true)
public class SqlOperationTracingTest {

    private static final String USER = "contract_user";
    private static final String USERNAME = "username";
    private static final String EMAIL = "email";
    private static final OperationOptions OPTIONS = new OperationOptions(Collections.emptyMap());

    private SqlTestDatabase database;
    private ContractTraceConnector connector;

    @BeforeMethod
    public void setUp() throws Exception {
        database = SqlTestDatabases.h2();
        database.initializeSchema();
        connector = new ContractTraceConnector();
        connector.init(database.configuration(true));
        // Warm the pool and schema detection so operations under test run against a live pool.
        connector.schema();
        CapturingLogProvider.clear();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
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
    public void createEmitsSqlProtocolEventsCorrelatedWithOperation() {
        var username = attribute(USERNAME);

        CapturingLogProvider.clear();
        connector.create(objectClass(), Set.of(
                AttributeBuilder.build(Name.NAME, "traced-create"),
                AttributeBuilder.build(username, "traced-create-user")), OPTIONS);

        var trace = singleTrace();
        assertThat(trace.operation()).isEqualTo("create");
        var sql = sqlWith(trace, "insert into", "traced-create-user");
        assertThat(paramValues(sql)).contains("traced-create-user");
    }

    @Test
    public void searchEmitsSqlProtocolEventsCorrelatedWithOperation() {
        var username = attribute(USERNAME);
        connector.create(objectClass(), Set.of(
                AttributeBuilder.build(Name.NAME, "traced-search"),
                AttributeBuilder.build(username, "traced-search-user")), OPTIONS);

        CapturingLogProvider.clear();
        var found = new ArrayList<ConnectorObject>();
        connector.executeQuery(objectClass(),
                FilterBuilder.equalTo(AttributeBuilder.build(username, "traced-search-user")), found::add, OPTIONS);
        assertThat(found).hasSize(1);

        var trace = singleTrace();
        assertThat(trace.operation()).isEqualTo("search");
        var sql = sqlWith(trace, "where", "traced-search-user");
        assertThat(sql.sql()).containsIgnoringCase("select");
        assertThat(paramValues(sql)).contains("traced-search-user");
    }

    @Test
    public void updateEmitsSqlProtocolEventsCorrelatedWithOperation() {
        var email = attribute(EMAIL);
        var uid = connector.create(objectClass(), Set.of(
                AttributeBuilder.build(Name.NAME, "traced-update"),
                AttributeBuilder.build(attribute(USERNAME), "traced-update-user")), OPTIONS);

        CapturingLogProvider.clear();
        connector.updateDelta(objectClass(), uid, Set.of(
                AttributeDeltaBuilder.build(email, List.of("traced-updated@example.com"))), OPTIONS);

        var trace = singleTrace();
        assertThat(trace.operation()).isEqualTo("update");
        var sql = sqlWith(trace, "update", "traced-updated@example.com");
        assertThat(paramValues(sql)).contains("traced-updated@example.com");
    }

    @Test
    public void deleteEmitsSqlProtocolEventsCorrelatedWithOperation() {
        var uid = connector.create(objectClass(), Set.of(
                AttributeBuilder.build(Name.NAME, "traced-delete"),
                AttributeBuilder.build(attribute(USERNAME), "traced-delete-user")), OPTIONS);

        CapturingLogProvider.clear();
        connector.delete(objectClass(), uid, OPTIONS);

        var trace = singleTrace();
        assertThat(trace.operation()).isEqualTo("delete");
        // The related-row cleanup deletes other tables first (e.g. CONTRACT_USER_ALIAS); scope to
        // the object class's own table.
        var sql = sqlWith(trace, "delete from " + USER + " ", null);
        assertThat(paramValues(sql))
                .anySatisfy(value -> assertThat(String.valueOf(value)).isEqualTo(uid.getUidValue()));
    }

    @Test
    public void emitsNoStructuredLinesWithoutDevelopmentMode() {
        var plain = new ContractTraceConnector();
        plain.init(database.configuration(false));
        try {
            plain.create(objectClass(), Set.of(
                    AttributeBuilder.build(Name.NAME, "plain-user"),
                    AttributeBuilder.build(USERNAME, "plain-user-name")), OPTIONS);
            assertThat(CapturingLogProvider.lines())
                    .noneMatch(line -> line.message().contains(ConndevLogFormat.MARKER));
        } finally {
            plain.dispose();
        }
    }

    private static ObjectClass objectClass() {
        return new ObjectClass(USER);
    }

    /** Resolves the object class attribute name for a column (the schema name may differ from it). */
    private String attribute(String columnName) {
        var objectClassInfo = connector.schema().getObjectClassInfo().stream()
                .filter(info -> USER.equalsIgnoreCase(info.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Object class not found: " + USER));
        return objectClassInfo.getAttributeInfo().stream()
                .filter(attribute -> columnName.equalsIgnoreCase(attribute.getName())
                        || columnName.equalsIgnoreCase(attribute.getNativeName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Attribute not found: " + USER + "." + columnName))
                .getName();
    }

    /**
     * Finds the first SQL protocol event of the trace whose text contains the fragment and, when
     * given, whose bound parameters contain the expected value.
     */
    private static ProtocolPayload sqlWith(OperationTrace trace, String fragment, String expectedParam) {
        return sqlProtocols(trace).stream()
                .filter(payload -> payload.sql() != null
                        && normalizedSql(payload.sql()).contains(fragment))
                .filter(payload -> expectedParam == null
                        || paramValues(payload).contains(expectedParam))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No SQL event matching '" + fragment + "' in " + sqlProtocols(trace)));
    }

    private static String normalizedSql(String sql) {
        return sql.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static List<ProtocolPayload> sqlProtocols(OperationTrace trace) {
        return trace.protocolEvents().stream()
                .map(event -> event.protocol())
                .filter(payload -> payload != null && ConndevLogFormat.PROTOCOL_SQL.equals(payload.type()))
                .toList();
    }

    private static List<Object> paramValues(ProtocolPayload payload) {
        return payload.params() == null
                ? List.of()
                : List.copyOf(payload.params().values());
    }

    private static OperationTrace singleTrace() {
        var lines = CapturingLogProvider.lines().stream()
                .map(CapturingLogProvider.CapturedLine::message)
                .toList();
        var traces = OperationLogParser.parse(lines);
        assertThat(traces).hasSize(1);
        return traces.get(0);
    }

    private static final class ContractTraceConnector
            extends AbstractGroovySqlConnector {

        private ContractTraceConnector() {
            super(false);
        }

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) {
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
        }
    }
}
