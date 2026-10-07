/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * Script validation against a real {@link ManifestBasedConnector}: validating a replacement for
 * an already-deployed operation script succeeds without conflicting with its own old content
 * (excluded from the sibling reload; see {@code ConnectorManifestTest} in conndev for direct
 * coverage of the exclusion itself).
 */
@Test(singleThreaded = true)
public class SqlManifestScriptValidationTest {

    private static final String URL = "jdbc:h2:mem:scriptvalidation;DB_CLOSE_DELAY=-1";
    private static final String MANIFEST_BASE = "/manifests/script-validation/connector.manifest";
    private static final String OPERATION_SCRIPT_RESOURCE = "/manifests/script-validation/Employee.op.groovy";

    private static class TestSqlConnector extends ManifestBasedConnector {
        TestSqlConnector() {
            super(MANIFEST_BASE);
            var config = new SqlConnectorConfiguration();
            config.setJdbcUrl(URL);
            config.setUsername("sa");
            config.setPassword(new GuardedString("".toCharArray()));
            config.setScanTables(true);
            config.setScanViews(true);
            config.setDevelopmentMode(true);
            TestSqlConnector.super.init(config);
        }
    }

    private void initTable() throws Exception {
        try (var c = DriverManager.getConnection(URL, "sa", "");
             var s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS employee CASCADE");
            s.execute("CREATE TABLE employee (id INT PRIMARY KEY, name VARCHAR(50) NOT NULL)");
            s.execute("INSERT INTO employee VALUES (1, 'alice')");
            s.execute("DROP TABLE IF EXISTS department CASCADE");
            s.execute("CREATE TABLE department (id INT PRIMARY KEY, name VARCHAR(50) NOT NULL)");
            s.execute("INSERT INTO department VALUES (1, 'engineering')");
        }
    }

    @Test
    public void validatingReplacementForExistingOperationScriptSucceeds() throws Exception {
        var result = validate("objectClass('Employee') { disableDelete() }", OPERATION_SCRIPT_RESOURCE);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validatingNewOperationScriptForNotYetDeployedObjectClassSucceeds() throws Exception {
        var result = validate("objectClass('Department') { disableDelete() }", null);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validate(String script, String filename) throws Exception {
        initTable();
        var arguments = new HashMap<String, Object>();
        arguments.put(ScriptValidationRequest.SCRIPT_ARGUMENT_OPERATION, ScriptValidationRequest.SCRIPT_OPERATION_BUILD);
        arguments.put(ScriptValidationRequest.SCRIPT_ARGUMENT_ARTIFACT_KIND, "operation");
        if (filename != null) {
            arguments.put(ScriptValidationRequest.SCRIPT_ARGUMENT_FILENAME, filename);
        }
        var context = new ScriptContext("groovy", script, arguments);
        var connector = new TestSqlConnector();
        return (Map<String, Object>) connector.runScriptOnResource(context, null);
    }

    // --------------------------------------------------------------------------
    // Multi-script overrides: no DB needed - incomplete config keeps initializeCore() local-only,
    // and ManifestBasedConnector's own manifest would otherwise have to list every override
    // up front (it unconditionally loads its whole manifest for real at init time, unlike
    // connector-scimrest's throwaway-builder schema branch) - overrides don't need a manifest
    // entry at all (see ClassHandlerConnectorBaseScriptValidationTest in conndev), so a minimal
    // ad-hoc connector is enough here.
    // --------------------------------------------------------------------------

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static class AdHocTestConnector extends AbstractGroovySqlConnector {
        AdHocTestConnector() {
            super(true);
            var config = new SqlConnectorConfiguration();
            config.setDevelopmentMode(true);
            init(config);
        }

        @Override
        protected void initializeSchema(GroovySchemaLoader loader) {
            loader.load("objectClass('Employee') { attribute('id').connId().type(String.class) }");
        }

        @Override
        protected void initializeObjectClassHandler(GroovyScriptLoader builder) {
        }
    }

    /**
     * No overrides, no broken siblings - just the primary candidate itself failing on its own.
     * The result must still be the unified "errors" array shape, not the older flat one, even
     * though there is exactly one error and nothing was combined with it.
     */
    @Test
    public void validatingBrokenPrimaryWithNoOverridesStillReturnsCombinedArrayShape() {
        var result = validateJson(new AdHocTestConnector(),
                "objectClass('Department') { disableDelete(", """
                {
                  "operation": "build",
                  "artifactKind": "operation"
                }
                """);

        assertEquals(result, json("""
                {
                  "status": "error",
                  "errors": [
                    {
                      "status": "error",
                      "phase": "compile",
                      "message": "startup failed:\\nScript1.groovy: 1: Unexpected input: '('Department') { disableDelete(' @ line 1, column 43.\\n   'Department') { disableDelete(\\n                                 ^\\n\\n1 error\\n",
                      "line": 1,
                      "column": 43
                    }
                  ]
                }
                """));
    }

    /**
     * A batch can have more than one broken override at once. Each broken one must get its own
     * error entry in the combined result - not just the first one encountered, with the rest
     * silently skipped.
     */
    @Test
    public void validatingWithTwoBrokenOverridesReportsAnErrorForEachOne() {
        var result = validateJson(new AdHocTestConnector(),
                "objectClass('Employee') { attribute('id').connId().type(String.class) }", """
                {
                  "operation": "build",
                  "artifactKind": "schema",
                  "overrides": {
                    "/Department.schema.groovy": "objectClass('Department') { attribute('id'",
                    "/Role.schema.groovy": "objectClass('Role') { attribute('id'"
                  }
                }
                """);

        var errors = new ArrayList<JsonNode>();
        result.get("errors").forEach(errors::add);
        errors.sort(Comparator.comparing(e -> e.get("source").asString()));
        assertEquals(result.get("status").asString(), "error", "Unexpected result: " + result);
        assertEquals(errors.size(), 2, "Unexpected errors: " + result);
        assertEquals(errors.get(0).get("source").asString(), "/Department.schema.groovy");
        assertEquals(errors.get(1).get("source").asString(), "/Role.schema.groovy");
    }

    /**
     * An operation override unrelated to the primary candidate is broken. The primary itself is
     * fine, but the batch as a whole isn't - the broken override is reported too, attributed to
     * its own filename, instead of being silently dropped just because the primary succeeded.
     */
    @Test
    public void validatingOperationCandidateStillReportsAnUnrelatedBrokenOperationOverride() {
        var result = validateJson(new AdHocTestConnector(),
                "objectClass('Employee') { disableDelete() }", """
                {
                  "operation": "build",
                  "artifactKind": "operation",
                  "overrides": {
                    "/Department.op.groovy": "objectClass('Department') { disableDelete("
                  }
                }
                """);

        assertEquals(result.get("status").asString(), "error", "Unexpected result: " + result);
        var errors = result.get("errors");
        assertEquals(errors.size(), 1, "Unexpected errors: " + result);
        assertEquals(errors.get(0).get("source").asString(), "/Department.op.groovy");
    }

    private static JsonNode json(String json) {
        return MAPPER.readTree(json);
    }

    @SuppressWarnings("unchecked")
    private static JsonNode validateJson(AdHocTestConnector connector, String script, String argumentsJson) {
        Map<String, Object> arguments = MAPPER.readValue(argumentsJson, Map.class);
        var context = new ScriptContext("groovy", script, arguments);
        Object result = connector.runScriptOnResource(context, null);
        return MAPPER.valueToTree(result);
    }
}
