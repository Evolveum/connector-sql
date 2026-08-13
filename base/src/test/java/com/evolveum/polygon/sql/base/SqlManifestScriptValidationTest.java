/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.groovy.ScriptValidationRequest;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.testng.annotations.Test;

import java.sql.DriverManager;
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
}
