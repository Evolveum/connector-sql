/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * YAML counterpart of {@link SqlManifestScriptValidationTest} — the language dispatch. Uses an
 * incomplete configuration (no JDBC URL) so schema init stays local, no live DB needed.
 *
 * <p>Unlike connector-scimrest, SQL's YAML front-end has no embedded-Groovy field anywhere today,
 * so there's nothing here to exercise the compile-phase syntax check against — it still runs on
 * every compile call, just finds zero fragments.
 */
public class YamlScriptValidationTest {

    // The connector's own startup schema — loaded via SqlSchemaDefinitionLoader.load(String), which
    // (unlike loadFromResource) is Groovy-only, so this must stay Groovy even though the candidate
    // scripts below are YAML.
    private static final String STARTUP_SCHEMA_SCRIPT = """
            objectClass('Person') {
                sql { table 'person' }
                attribute('id') { connId { name '__UID__' } }
            }
            """;

    private static final String SCHEMA_SCRIPT = """
            objectClasses:
              Person:
                sql:
                  table: person
                attributes:
                  id:
                    connId:
                      name: __UID__
            """;

    private static final String VALID_OPERATION_SCRIPT = """
            objectClasses:
              Person:
                create:
                  enabled: false
            """;

    private static final String OPERATION_SCRIPT_WITH_STRUCTURAL_ERROR = """
            objectClasses:
              Bogus:
                create:
                  enabled: false
            """;

    private static class TestConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        TestConnector() {
            super(true);
        }

        @Override
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) {
            loader.load(STARTUP_SCHEMA_SCRIPT);
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) {
        }
    }

    private static TestConnector connector() {
        var config = new SqlConnectorConfiguration();
        config.setDevelopmentMode(true);
        var connector = new TestConnector();
        connector.init(config);
        return connector;
    }

    @Test
    public void validYamlSchemaScriptPassesValidation() {
        var result = validate(SCHEMA_SCRIPT, "schema", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void validYamlOperationScriptPassesValidation() {
        var result = validate(VALID_OPERATION_SCRIPT, "operation", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void yamlOperationCompileNeverBuildsAnythingEvenWhenTextIsValid() {
        // operation="compile" only runs GroovySyntaxChecker (no builder touched at all) — a
        // structurally invalid document (referencing an object class the schema doesn't have)
        // would only fail at operation="build", so seeing "ok" here for valid YAML text, without
        // ever loading it onto a real builder, is the observable proof of that split.
        var result = validate(VALID_OPERATION_SCRIPT, "operation", ScriptValidationRequest.SCRIPT_OPERATION_COMPILE);

        assertEquals(result.get("status"), "ok", "Unexpected result: " + result);
    }

    @Test
    public void structuralErrorInOperationScriptFailsAtBuildNotCompile() {
        var compileResult = validate(
                OPERATION_SCRIPT_WITH_STRUCTURAL_ERROR, "operation", ScriptValidationRequest.SCRIPT_OPERATION_COMPILE);
        assertEquals(compileResult.get("status"), "ok",
                "Compile must not catch structural errors: " + compileResult);

        var buildResult = validate(
                OPERATION_SCRIPT_WITH_STRUCTURAL_ERROR, "operation", ScriptValidationRequest.SCRIPT_OPERATION_BUILD);
        assertEquals(buildResult.get("status"), "error", "Unexpected result: " + buildResult);
    }

    private static Map<String, Object> validate(String script, String artifactKind, String operation) {
        return validate(connector(), script, artifactKind, operation);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> validate(TestConnector connector, String script, String artifactKind, String operation) {
        var context = new ScriptContext("yaml", script, Map.of(
                ScriptValidationRequest.SCRIPT_ARGUMENT_OPERATION, operation,
                ScriptValidationRequest.SCRIPT_ARGUMENT_ARTIFACT_KIND, artifactKind));
        return (Map<String, Object>) connector.runScriptOnResource(context, null);
    }
}
